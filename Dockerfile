# ===== 빌드 스테이지 =====
FROM eclipse-temurin:17-jdk-alpine AS builder

WORKDIR /app

# 의존성 캐싱을 위해 gradle 파일 먼저 복사
COPY build.gradle settings.gradle ./
COPY gradle ./gradle
COPY gradlew .
RUN chmod +x gradlew

# 의존성 다운로드 (폐쇄망 환경에서는 Gradle 캐시 볼륨 활용 권장)
RUN ./gradlew dependencies --no-daemon --quiet 2>/dev/null || true

# 소스 코드 복사 및 빌드
COPY src ./src
RUN ./gradlew bootJar --no-daemon -x test

# ===== 런타임 스테이지 =====
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# 데이터 디렉토리 생성
RUN mkdir -p /app/data

# 비root 사용자 생성 (보안)
RUN addgroup -S ozrgroup && adduser -S ozruser -G ozrgroup
RUN chown -R ozruser:ozrgroup /app

COPY --from=builder --chown=ozruser:ozrgroup /app/build/libs/*.jar app.jar

USER ozruser

EXPOSE 8080

ENTRYPOINT ["java", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=75.0", \
    "-jar", "app.jar"]
