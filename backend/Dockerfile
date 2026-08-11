# ════════════════════════════════════════
# Stage 1: Build
# ════════════════════════════════════════
FROM eclipse-temurin:17-jdk-alpine AS builder

WORKDIR /build

# Gradle Wrapper 캐시 레이어 분리
COPY gradlew gradlew.bat ./
COPY gradle/ gradle/
RUN chmod +x gradlew

# 의존성 다운로드 (소스 변경 시 캐시 재사용)
COPY build.gradle settings.gradle ./
RUN ./gradlew dependencies --no-daemon -q 2>/dev/null || true

# 소스 복사 및 빌드
COPY src/ src/
RUN ./gradlew bootJar --no-daemon -x test

# ════════════════════════════════════════
# Stage 2: Runtime
# ════════════════════════════════════════
FROM eclipse-temurin:17-jre-alpine AS runtime

# 보안: 비루트 사용자로 실행
RUN addgroup -S attendance && adduser -S attendance -G attendance

WORKDIR /app

# Spring Boot JAR 복사
COPY --from=builder /build/build/libs/*.jar app.jar

# 헬스체크용 curl 설치
RUN apk add --no-cache curl

# 업로드된 로고 저장 디렉터리 (docker-compose의 logo-data 볼륨 마운트 지점)
RUN mkdir -p /data/logo

# 파일 소유권 변경
RUN chown -R attendance:attendance /app /data/logo

USER attendance

EXPOSE 8080

# JVM 메모리 설정 (컨테이너 메모리 기준 자동 조정)
ENV JAVA_OPTS="-XX:+UseContainerSupport \
               -XX:MaxRAMPercentage=75.0 \
               -XX:+UseG1GC \
               -Djava.security.egd=file:/dev/./urandom \
               -Dspring.output.ansi.enabled=ALWAYS"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
