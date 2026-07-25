FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /app

COPY pom.xml .
# lombok.config afecta el codigo generado por Lombok. Sin copiarlo, la imagen se construye con
# semantica distinta a la del build local, que es como se colo un fallo de arranque en produccion.
COPY lombok.config .
COPY src ./src

RUN mvn -B -DskipTests clean package

FROM eclipse-temurin:21-jre
WORKDIR /app

COPY --from=build /app/target/backend-0.0.1-SNAPSHOT.jar /app/backend.jar

EXPOSE 8080

# Sin limite explicito la JVM toma solo el 25% de la RAM del contenedor (128MB en un Render
# Starter de 512MB), lo que provoca OutOfMemoryError con pocas subidas concurrentes.
# Se puede sobrescribir desde las variables de entorno del servicio.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=70.0 -XX:+ExitOnOutOfMemoryError"

CMD ["sh", "-c", "exec java $JAVA_OPTS -jar /app/backend.jar"]
