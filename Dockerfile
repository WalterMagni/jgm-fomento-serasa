FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app

COPY pom.xml .
RUN apk add --no-cache maven && \
    mvn dependency:go-offline -B

COPY src ./src
RUN mvn package -DskipTests -B

# Security Best Practice: Use a distroless or lightweight JRE and non-root user
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S spring && adduser -S spring -G spring
# Arquivos das esteiras (anexos e documentos). O volume nomeado herda este dono na primeira subida.
RUN mkdir -p /app/dados/esteiras && chown -R spring:spring /app/dados
USER spring:spring

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080
# Fuso de São Paulo: a imagem roda em UTC, e todo horário gravado pelo portal (LocalDateTime) saía
# 3 h adiantado no histórico. O banco de fuso vem no próprio Java, não depende de tzdata.
ENTRYPOINT ["java", "-Duser.timezone=America/Sao_Paulo", "-jar", "app.jar"]
