# ---- Etapa 1: build ----
# Compila a aplicacao dentro de um container com Maven + JDK 21, para que
# quem for rodar isso nao precise ter Java/Maven instalados localmente.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# O cache do Maven persiste entre builds, mesmo quando o codigo muda.
COPY pom.xml .
COPY src ./src
RUN --mount=type=cache,id=maven-repository,target=/root/.m2/repository \
    mvn -B package -DskipTests

# ---- Etapa 2: imagem final de execucao ----
# Imagem enxuta, so com o JRE (nao o JDK completo) e o .jar gerado.
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S app && adduser -S app -G app
USER app

COPY --from=build /build/target/whatsapp-chatbot-api.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
