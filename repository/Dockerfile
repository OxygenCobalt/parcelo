FROM gradle:7-jdk11 AS build
COPY --chown=gradle:gradle . /home/gradle/src
RUN curl -OL https://github.com/protocolbuffers/protobuf/releases/download/v25.1/protoc-25.1-linux-x86_64.zip \
    && unzip protoc-25.1-linux-x86_64.zip -d /usr/local \
    && rm protoc-25.1-linux-x86_64.zip
WORKDIR /home/gradle/src
RUN gradle repository:buildFatJar --no-daemon --dependency-verification off

FROM openjdk:17
EXPOSE 8081:8081
RUN mkdir /parcelo /parcelo/db /parcelo/publish
ENV REPOSITORY_DATABASE_PATH=/parcelo/db/repository.db
ENV REPOSITORY_PUBLISH_DIR=/parcelo/publish
COPY --from=build /home/gradle/src/repository/build/libs/repository-all.jar /parcelo/parcelo-repository.jar
ENTRYPOINT ["java", "-Dio.ktor.development=true", "-jar", "/parcelo/parcelo-repository.jar"]