# Dockerfile（Day 10 部署）
FROM openjdk:17-jdk-slim

WORKDIR /app

# 复制编译后的jar包
COPY target/ai-review-assistant-0.0.1-SNAPSHOT.jar app.jar

# 暴露端口
EXPOSE 8080

# 启动命令
ENTRYPOINT ["java", "-jar", "app.jar"]
