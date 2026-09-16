# ---- Etapa 1: build ----
# Compila a aplicacao dentro de um container com Maven + JDK 21, para que
# quem for rodar isso nao precise ter Java/Maven instalados localmente.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Copia primeiro apenas o pom.xml para aproveitar o cache de dependencias
# do Docker entre builds (só refaz o download se o pom.xml mudar).
COPY pom.xml .
RUN mvn -B dependency:go-offline

COPY src ./src
RUN mvn -B clean package -DskipTests

# ---- Etapa 2: imagem final de execucao ----
# Imagem enxuta, so com o JRE (nao o JDK completo) e o .jar gerado.
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S app && adduser -S app -G app
USER app

COPY --from=build /build/target/whatsapp-chatbot-api.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
