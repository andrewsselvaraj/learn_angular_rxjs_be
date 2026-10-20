# Image built from source, for builds outside OpenShift (for example to push to an external registry).
# In-cluster builds use the S2I BuildConfig instead.
#
# Build from the repo root:
#   docker build -f deploy/docker/Dockerfile -t learn-angular-rxjs-be .

FROM registry.access.redhat.com/ubi9/openjdk-21:1.21 AS build
WORKDIR /home/default/app
COPY --chown=185:0 . .
RUN mvn -B package -DskipTests && \
    cp target/learn-angular-rxjs-be-0.0.1-SNAPSHOT.jar /tmp/app.jar

FROM registry.access.redhat.com/ubi9/openjdk-21-runtime:1.21
# run-java.sh in this image starts the single jar found in /deployments and sizes
# the heap from the container memory limit.
COPY --from=build --chown=185:0 /tmp/app.jar /deployments/app.jar
USER 185
