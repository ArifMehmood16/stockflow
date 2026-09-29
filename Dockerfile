FROM eclipse-temurin:25-jdk-alpine
WORKDIR /app
COPY tools ./tools
RUN java tools/Build.java compile
COPY prototype ./prototype
COPY infra ./infra
RUN adduser -D -u 10001 lab && chown -R lab:lab /app
USER lab
ENV HOST=0.0.0.0 PORT=4173
EXPOSE 4173
CMD ["java", "-Xmx96m", "-cp", ".lab/classes:.lab/lib/*", "stockflow.Lab", "preview"]
