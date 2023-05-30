package app.accrescent.parcelo.apksparser

import com.android.apksig.ApkVerifier
import com.android.apksig.apk.ApkFormatException
import com.android.apksig.apk.ApkUtils
import com.android.apksig.util.DataSources
import com.android.bundle.Commands.BuildApksResult
import com.android.tools.apk.analyzer.BinaryXmlParser
import com.github.zafarkhaja.semver.ParseException
import com.github.zafarkhaja.semver.Version
import com.google.protobuf.InvalidProtocolBufferException
import java.io.InputStream
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.security.cert.X509Certificate
import java.util.Optional
import java.util.zip.ZipInputStream

public data class ApkSetMetadata(
    val appId: String,
    val versionCode: Int,
    val versionName: String,
    val targetSdk: Int,
    val bundletoolVersion: String,
    val reviewIssues: List<String>,
    val abiSplits: Set<String>,
    val densitySplits: Set<String>,
    val langSplits: Set<String>,
    val entrySplitNames: Map<String, Optional<String>>,
)

private const val ANDROID_MANIFEST = "AndroidManifest.xml"

/**
 * The minimum acceptable bundletool version used to generate the APK set. This version is taken
 * from a recent Android Studio release.
 */
private val MIN_BUNDLETOOL_VERSION = Version.Builder("1.11.4").build()

/**
 * Parses an APK set into its metadata
 *
 * For now this function attempts to determine whether the APK set is valid on a best-effort
 * basis, so it may accept files which are not strictly valid APK sets. However, any APK set it
 * rejects is certainly invalid. It currently accepts the given file as a valid APK set according
 * to the following criteria:
 *
 * - the input file is a valid ZIP
 * - a valid APK is a ZIP with each of the following:
 *     - a v2 or v3 APK signature which passes verification and is generated by a non-debug
 *     certificate
 *     - a valid Android manifest at the expected path
 * - all non-directory entries in said ZIP except for "toc.pb" are valid APKs
 * - "toc.pb" is a valid BuildApksResult protocol buffer
 * - the input ZIP contains at least one APK
 * - all APKs must not be debuggable
 * - all APKs must not be marked test only
 * - all APKs have the same signing certificates
 * - all APKs have the same app ID and version code
 * - all APKs have unique split names
 * - exactly one APK is a base APK (i.e., has an empty split name)
 * - the base APK specifies a version name
 * - the app ID is well-formed
 *
 * @return metadata describing the APK set and the app it represents
 * @throws InvalidApkSetException the APK set is invalid
 */
public fun parseApkSet(file: InputStream): ApkSetMetadata {
    var bundletoolVersion: String? = null
    var metadata: ApkSetMetadata? = null
    var pinnedCertHashes = emptyList<String>()
    val splitNames = mutableSetOf<String?>()

    ZipInputStream(file).use { zip ->
        generateSequence { zip.nextEntry }.filterNot { it.isDirectory }.forEach { entry ->
            val entryBytes = zip.readBytes()
            val entryDataSource = DataSources.asDataSource(ByteBuffer.wrap(entryBytes))

            // Parse metadata
            if (entry.name == "toc.pb") {
                val bundletoolMetadata = try {
                    BuildApksResult.newBuilder().mergeFrom(entryBytes).build()
                } catch (e: InvalidProtocolBufferException) {
                    throw InvalidApkSetException("bundletool metadata not valid")
                }
                // Validate bundletool version
                val parsedBundletoolVersion = try {
                    Version.Builder(bundletoolMetadata.bundletool.version).build()
                } catch (e: ParseException) {
                    throw InvalidApkSetException("invalid bundletool version")
                }
                if (parsedBundletoolVersion >= MIN_BUNDLETOOL_VERSION) {
                    bundletoolVersion = parsedBundletoolVersion.toString()
                } else {
                    throw InvalidApkSetException(
                        "APK set generated with bundletool $parsedBundletoolVersion" +
                                " but minimum supported version is $MIN_BUNDLETOOL_VERSION"
                    )
                }
                return@forEach
            }

            // Everything else is an APK. Start by finding and verifying its signature.
            val sigCheckResult = try {
                ApkVerifier.Builder(entryDataSource).build().verify()
            } catch (e: ApkFormatException) {
                throw InvalidApkSetException("an APK is malformed")
            }

            if (sigCheckResult.isVerified) {
                if (!(sigCheckResult.isVerifiedUsingV2Scheme || sigCheckResult.isVerifiedUsingV3Scheme)) {
                    throw InvalidApkSetException("APK signature isn't at least v2 or v3")
                } else if (sigCheckResult.signerCertificates.any { it.isDebug() }) {
                    throw InvalidApkSetException("APK signed with debug certificate")
                }
            } else {
                throw InvalidApkSetException("APK signature doesn't verify")
            }

            // Pin the APK signing certificates on the first APK encountered to ensure split APKs
            // can actually be installed.
            if (pinnedCertHashes.isEmpty()) {
                pinnedCertHashes = sigCheckResult.signerCertificates.map { it.fingerprint() }
            } else {
                // Check against pinned certificates
                val theseCertHashes = sigCheckResult.signerCertificates.map { it.fingerprint() }
                if (theseCertHashes != pinnedCertHashes) {
                    throw InvalidApkSetException("APK signing certificates don't match each other")
                }
            }

            // Parse the Android manifest
            val manifest = try {
                val manifestBytes = ApkUtils.getAndroidManifest(entryDataSource).moveToByteArray()
                val decodedManifest = BinaryXmlParser.decodeXml(ANDROID_MANIFEST, manifestBytes)
                manifestReader.readValue<AndroidManifest>(decodedManifest)
            } catch (e: ApkFormatException) {
                throw InvalidApkSetException("an APK is malformed")
            }

            if (!splitNames.add(manifest.split)) {
                throw InvalidApkSetException("duplicate split names found")
            }

            if (manifest.application.debuggable == true) {
                throw InvalidApkSetException("application is debuggable")
            }
            if (manifest.application.testOnly == true) {
                throw InvalidApkSetException("application is test only")
            }

            // Pin the app metadata on the first manifest parsed to ensure all split APKs have the
            // same app ID and version code.
            if (metadata == null) {
                // Validate the app ID
                if (!isValidAppId(manifest.`package`)) {
                    throw InvalidApkSetException("app ID ${manifest.`package`} is not valid")
                }
                metadata =
                    ApkSetMetadata(
                        manifest.`package`,
                        manifest.versionCode,
                        "",
                        0,
                        "",
                        emptyList(),
                        emptySet(),
                        emptySet(),
                        emptySet(),
                        emptyMap(),
                    )
            } else {
                // Check that the metadata is the same as that previously pinned (sans the version
                // name for reasons described above).
                //
                // We can non-null assert the metadata here since the changing closure is called
                // sequentially.
                if (manifest.`package` != metadata!!.appId || manifest.versionCode != metadata!!.versionCode) {
                    throw InvalidApkSetException("APK manifest info is not consistent across all APKs")
                }
            }

            // Update the review issues, version name, and target SDK if this is the base APK
            if (manifest.split == null) {
                // Permissions
                manifest.usesPermissions?.let { permissions ->
                    metadata = metadata!!.copy(reviewIssues = permissions.map { it.name })
                }
                // Service intent filter actions
                manifest.application.services?.let { services ->
                    val issues = metadata!!.reviewIssues.toMutableSet()
                    services
                        .flatMap { it.intentFilters ?: emptyList() }
                        .flatMap { it.actions }
                        .map { it.name }
                        .forEach { issues.add(it) }
                    metadata = metadata!!.copy(reviewIssues = issues.toList())
                }

                // Version name
                if (manifest.versionName != null) {
                    metadata = metadata!!.copy(versionName = manifest.versionName)
                } else {
                    throw InvalidApkSetException("base APK doesn't specify a version name")
                }

                // Target SDK
                if (manifest.usesSdk != null) {
                    metadata = metadata!!.copy(
                        targetSdk = manifest.usesSdk.targetSdkVersion
                            ?: manifest.usesSdk.minSdkVersion
                    )
                } else {
                    throw InvalidApkSetException("base APK doesn't specify a target SDK")
                }
            }

            // Update the entry name -> split mapping
            val map = metadata!!.entrySplitNames.toMutableMap()
            map[entry.name] = if (manifest.split == null) {
                Optional.empty()
            } else {
                Optional.of(manifest.split.substringAfter("config."))
            }
            metadata = metadata!!.copy(entrySplitNames = map)
        }
    }

    // Update metadata with split config names
    val (abiSplits, langSplits, densitySplits) = run {
        val abiSplits = mutableSetOf<String>()
        val langSplits = mutableSetOf<String>()
        val densitySplits = mutableSetOf<String>()

        for (splitName in splitNames) {
            splitName?.let {
                try {
                    when (getSplitTypeForName(splitName)) {
                        SplitType.ABI -> abiSplits
                        SplitType.LANGUAGE -> langSplits
                        SplitType.SCREEN_DENSITY -> densitySplits
                    }.add(splitName.substringAfter("config."))
                } catch (e: SplitNameNotConfigException) {
                    throw InvalidApkSetException(e.message!!)
                }
            }
        }

        Triple(abiSplits.toSet(), langSplits.toSet(), densitySplits.toSet())
    }
    metadata = metadata?.copy(
        abiSplits = abiSplits,
        langSplits = langSplits,
        densitySplits = densitySplits
    )

    if (bundletoolVersion != null) {
        metadata = metadata?.copy(bundletoolVersion = bundletoolVersion!!)
    } else {
        throw InvalidApkSetException("no bundletool version found")
    }

    // If there isn't a base APK, freak out
    if (!splitNames.contains(null)) {
        throw InvalidApkSetException("no base APK found")
    }

    return metadata ?: throw InvalidApkSetException("no APKs found")
}

/**
 * Returns whether this is a debug certificate generated by the Android SDK tools
 */
private fun X509Certificate.isDebug(): Boolean {
    return subjectX500Principal.name == "C=US,O=Android,CN=Android Debug"
}

/**
 * Gets the certificate's SHA256 fingerprint
 */
private fun X509Certificate.fingerprint(): String {
    return MessageDigest
        .getInstance("SHA-256")
        .digest(this.encoded)
        .joinToString("") { "%02x".format(it) }
}

public enum class SplitType { ABI, LANGUAGE, SCREEN_DENSITY }

private val abiSplitNames = setOf("arm64_v8a", "armeabi_v7a", "x86", "x86_64")
private val densitySplitNames =
    setOf("ldpi", "mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi", "nodpi", "tvdpi")

/**
 * Detects the configuration split API type based on its name
 *
 * @throws SplitNameNotConfigException the split name is not a valid configuration split name
 */
private fun getSplitTypeForName(splitName: String): SplitType {
    val configName = splitName.substringAfter("config.")
    if (configName == splitName) {
        throw SplitNameNotConfigException(splitName)
    }

    return if (abiSplitNames.contains(configName)) {
        SplitType.ABI
    } else if (densitySplitNames.contains(configName)) {
        SplitType.SCREEN_DENSITY
    } else {
        SplitType.LANGUAGE
    }
}

/**
 * Returns whether the given string is a valid Android application ID according to
 * https://developer.android.com/studio/build/configure-app-module. Specifically, it verifies:
 *
 * 1. The string contains two segments (one or more dots).
 * 2. Each segment starts with a letter.
 * 3. All characters are alphanumeric or an underscore.
 *
 * If any of these conditions are not met, verification fails and this function return false.
 */

private val alphanumericUnderscoreRegex = Regex("""^[a-zA-Z0-9_]+$""")

private fun isValidAppId(appId: String): Boolean {
    val segments = appId.split(".")
    if (segments.size < 2) {
        return false
    }

    for (segment in segments) {
        when {
            segment.isEmpty() -> return false
            !segment[0].isLetter() -> return false
            !alphanumericUnderscoreRegex.matches(segment) -> return false
        }
    }

    return true
}

public class InvalidApkSetException(message: String) : Exception(message)

public class SplitNameNotConfigException(splitName: String) :
    Exception("split name $splitName is not a config split name")
