ARG GITHUB_CLIENT_ID
ARG GITHUB_CLIENT_SECRET
ARG REPOSITORY_API_KEY

FROM gradle:7-jdk11 AS build
COPY --chown=gradle:gradle . /home/gradle/src
RUN curl -OL https://github.com/protocolbuffers/protobuf/releases/download/v25.1/protoc-25.1-linux-x86_64.zip \
    && unzip protoc-25.1-linux-x86_64.zip -d /usr/local \
    && rm protoc-25.1-linux-x86_64.zip
RUN apt-get update && apt-get install -y xz-utils \
    && curl -OL https://nodejs.org/dist/v20.10.0/node-v20.10.0-linux-x64.tar.xz \
    && tar -xf ./node-v20.10.0-linux-x64.tar.xz \
    && mv node-v20.10.0-linux-x64/bin/* /usr/local/bin/ \
    && mv node-v20.10.0-linux-x64/lib/* /usr/local/lib/ \
    && mv node-v20.10.0-linux-x64/include/* /usr/local/include/ \
    && mv node-v20.10.0-linux-x64/share/* /usr/local/share/
WORKDIR /home/gradle/src
RUN gradle console:buildFrontendDebug --no-daemon --dependency-verification off
RUN gradle console:buildFatJar --no-daemon --dependency-verification off

FROM openjdk:17
EXPOSE 8081:8081
RUN mkdir /parcelo /parcelo/db /parcelo/uploads
ENV CONSOLE_DATABASE_PATH=/parcelo/db/console.db
ENV FILE_STORAGE_BASE_DIR=/parcelo/uploads
COPY --from=build /home/gradle/src/console/frontend/dist /console/frontend/dist
COPY --from=build /home/gradle/src/console/build/libs/console-all.jar /parcelo/parcelo-console.jar
ENTRYPOINT ["java", "-Dio.ktor.development=true", "-jar", "/parcelo/parcelo-console.jar"]