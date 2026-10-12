
FROM eclipse-temurin:21-jdk

WORKDIR /app

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B dependency:go-offline

COPY src/ src/
RUN ./mvnw -B clean package -DskipTests

EXPOSE 8080

CMD ["sh", "-c", "echo '=== TiDB DNS test ==='; getent hosts \"$MYSQL_HOST\" || true; echo '=== TiDB TCP test ==='; timeout 5 bash -c 'exec 3<>/dev/tcp/$MYSQL_HOST/$MYSQL_PORT' && echo 'TCP_CONNECTION_SUCCESS' || echo 'TCP_CONNECTION_FAILED'; echo '=== Starting Spring Boot ==='; exec java -jar target/demo-0.0.1-SNAPSHOT.jar"]
