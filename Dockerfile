# ================= 构建阶段 =================
FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /build
COPY . /build
RUN mvn -B -DskipTests package

# ================= 运行阶段 =================
FROM eclipse-temurin:17-jre-jammy

# fontconfig + 字体：PDFBox/Tika 渲染扫描件时会用到 AWT，缺字体会抛 HeadlessException 相关错误
RUN apt-get update \
    && apt-get install -y --no-install-recommends fontconfig fonts-liberation tzdata \
    && rm -rf /var/lib/apt/lists/*

ENV TZ=Asia/Shanghai \
    LANG=C.UTF-8 \
    SPRING_PROFILES_ACTIVE=prod \
    SERVER_PORT=8080

WORKDIR /app
COPY --from=builder /build/anxin-web/target/anxin-web-*.jar /app/app.jar

EXPOSE 8080

# exec 形式让 java 成为 PID 1，云托管回收实例时 SIGTERM 才能直达进程触发优雅停机
ENTRYPOINT ["java", \
  "-XX:MaxRAMPercentage=70.0", \
  "-XX:+ExitOnOutOfMemoryError", \
  "-Djava.awt.headless=true", \
  "-jar", "/app/app.jar"]
