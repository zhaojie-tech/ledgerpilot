# LedgerPilot 后端镜像（多阶段：Maven 构建 → JRE 运行）
FROM maven:3.8-eclipse-temurin-17 AS build
WORKDIR /src
COPY apps/backend .
RUN mvn -q -pl ledgerpilot-admin -am package -DskipTests

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /src/ledgerpilot-admin/target/ledgerpilot-admin.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
