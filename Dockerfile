FROM eclipse-temurin:25-jdk-alpine@sha256:3fd2d245c4e0eba615fe366a71b8bd25f5db7104f53e4026b24bf508b880bd2a
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
