FROM eclipse-temurin:17-jre
WORKDIR /app
COPY target/*.jar app.jar
EXPOSE 8081
# Force production mode even if jar wasn't built with -Pproduction
ENTRYPOINT ["java", "-Dvaadin.productionMode=true", "-jar", "app.jar"]
