# ---- build ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
# Baixa as dependências numa camada própria: só é refeita quando o pom.xml muda
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q package -DskipTests

# ---- runtime ----
FROM eclipse-temurin:21-jre
WORKDIR /app
# Atualizações de segurança do sistema base: sem isso o scan do Trivy no CI barra a imagem por
# CVEs já corrigidas no Ubuntu e ainda não incorporadas à tag eclipse-temurin
RUN apt-get update \
	&& apt-get -y upgrade --no-install-recommends \
	&& rm -rf /var/lib/apt/lists/*
# UID numérico fixo: o Kubernetes só consegue verificar o runAsNonRoot com usuário numérico
RUN groupadd --system --gid 10001 app && useradd --system --uid 10001 --gid app app
COPY --from=build /app/target/oficina-os-service-*.jar app.jar
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
