FROM eclipse-temurin:25-jdk-alpine AS builder

WORKDIR /app

ARG GITHUB_ACTOR
ARG GITHUB_TOKEN
ENV GITHUB_ACTOR=${GITHUB_ACTOR}
ENV GITHUB_TOKEN=${GITHUB_TOKEN}

COPY . .

RUN chmod +x gradlew && ./gradlew clean build -x test

FROM eclipse-temurin:25-jre-alpine AS runtime

WORKDIR /app

# 비-root 실행 — 컨테이너가 침해돼도 root 권한을 넘기지 않고, 마운트된 호스트 볼륨에
# root 소유 파일이 쌓이지 않게 한다(호스트에서 백업·정리 시 sudo 불필요).
# UID/GID 는 호스트 볼륨 소유권과 맞아야 하므로 고정한다 — Jenkinsfile 이 같은 값으로
# 마운트 디렉터리를 chown 하고 build-arg 로도 같은 값을 넘긴다.
ARG APP_UID=1000
ARG APP_GID=1000
RUN addgroup -g ${APP_GID} porest \
    && adduser -u ${APP_UID} -G porest -D -H -h /app porest

COPY --from=builder --chown=porest:porest /app/build/libs/*.jar app.jar

USER porest

EXPOSE 8002

ENTRYPOINT ["java", "-jar", "app.jar"]
