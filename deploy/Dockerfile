# 只跑 jar 的镜像：构建在本地完成，云托管不再跑 maven。
# 与仓库根的 Dockerfile 二选一，配合 scripts/package-cloudrun.sh 生成的代码包使用。
# 代码包结构必须是 Dockerfile 与 app.jar 同级，所以这里 COPY 的是固定文件名。
FROM eclipse-temurin:17-jre-jammy

# fontconfig + 字体：PDFBox/Tika 渲染扫描件时会走 AWT，缺字体会在解析阶段报错
RUN apt-get update \
    && apt-get install -y --no-install-recommends fontconfig fonts-liberation tzdata \
    && rm -rf /var/lib/apt/lists/*

ENV TZ=Asia/Shanghai \
    LANG=C.UTF-8 \
    SPRING_PROFILES_ACTIVE=prod \
    SERVER_PORT=8080

WORKDIR /app
COPY app.jar /app/app.jar

EXPOSE 8080

# exec 形式让 java 成为 PID 1，实例回收时 SIGTERM 才能直达进程触发优雅停机
ENTRYPOINT ["java", \
  "-XX:MaxRAMPercentage=70.0", \
  "-XX:+ExitOnOutOfMemoryError", \
  "-Djava.awt.headless=true", \
  "-jar", "/app/app.jar"]
