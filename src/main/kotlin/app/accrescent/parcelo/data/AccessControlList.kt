package app.accrescent.parcelo.data

import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.ReferenceOption

object AccessControlLists : IntIdTable("access_control_lists") {
    val userId = reference("user_id", Users, ReferenceOption.CASCADE)
    val appId = reference("app_id", Apps, ReferenceOption.CASCADE)

    init {
        uniqueIndex(userId, appId)
    }
}

class AccessControlList(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<AccessControlList>(AccessControlLists)

    var userId by AccessControlLists.userId
    var appId by AccessControlLists.appId
}
