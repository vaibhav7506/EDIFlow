FROM eclipse-temurin:21-jdk-jammy
RUN apt-get update && apt-get install -y --no-install-recommends openssl && rm -rf /var/lib/apt/lists/*
COPY generate-certs.sh /usr/local/bin/generate-certs.sh
ENTRYPOINT ["bash", "/usr/local/bin/generate-certs.sh"]
