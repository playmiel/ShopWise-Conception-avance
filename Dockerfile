# syntax=docker/dockerfile:1.7
# ShopWise API - image de production en deux etapes.

# ---- Etape 1 : compilation (JDK + Maven wrapper) ----
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY mvnw pom.xml ./
COPY .mvn .mvn
# Fins de ligne Unix (depot clone sous Windows) et droit d'execution
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw
# Dependances dans une couche a part : re-telechargees seulement si le pom change
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q dependency:go-offline
COPY src src
# Les tests sont executes par "mvnw verify" (poste de dev / CI) ; l'image ne fait que packager
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q package -DskipTests \
 && java -Djarmode=tools -jar target/app-*.jar extract --layers --launcher --destination target/extracted \
 && mkdir -p target/extracted/dependencies target/extracted/spring-boot-loader \
             target/extracted/snapshot-dependencies target/extracted/application

# ---- Etape 2 : execution (JRE seul, utilisateur sans privileges) ----
FROM eclipse-temurin:21-jre
RUN groupadd --system shopwise && useradd --system --gid shopwise --no-create-home shopwise
WORKDIR /app
# Couches de la moins a la plus changeante : une modification du code ne reconstruit que la derniere
COPY --from=build /workspace/target/extracted/dependencies/ ./
COPY --from=build /workspace/target/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/target/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/target/extracted/application/ ./
USER shopwise
EXPOSE 8080
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
# Pret = comptes crees, donnees chargees, modele de recommandation entraine
HEALTHCHECK --interval=10s --timeout=3s --start-period=60s --retries=5 \
  CMD curl -fs http://localhost:8080/actuator/health/readiness || exit 1
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]