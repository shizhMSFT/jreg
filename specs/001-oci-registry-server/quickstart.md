# jreg Quickstart Guide

Get started with local development of jreg, an OCI-compliant container registry server.

## Prerequisites

Ensure you have the following installed:

- **Java 21 (LTS)**: Required for virtual threads support
  - Download: [Eclipse Temurin](https://adoptium.net/) or [Oracle JDK](https://www.oracle.com/java/technologies/downloads/)
  - Verify: `java -version` (should show 21.x.x)

- **Maven 3.9+**: Build and dependency management
  - Download: [Apache Maven](https://maven.apache.org/download.cgi)
  - Verify: `mvn -version`

- **Docker 20.10+**: For testing with real Docker CLI
  - Download: [Docker Desktop](https://www.docker.com/products/docker-desktop/)
  - Verify: `docker --version`

- **Git**: Version control
  - Download: [Git SCM](https://git-scm.com/downloads)
  - Verify: `git --version`

- **AWS CLI** (Optional): For interacting with real S3
  - Download: [AWS CLI](https://aws.amazon.com/cli/)
  - Verify: `aws --version`

---

## Quick Start (5 minutes)

### 1. Clone the Repository

```bash
git clone https://github.com/yourusername/jreg.git
cd jreg
```

### 2. Start LocalStack (S3 Mock)

For local development, we use LocalStack to simulate AWS S3:

```bash
# Using Docker Compose (recommended)
docker compose up -d localstack

# Or start LocalStack directly
docker run -d \
  --name jreg-localstack \
  -p 4566:4566 \
  -e SERVICES=s3 \
  -e DEBUG=1 \
  localstack/localstack:latest
```

Verify LocalStack is running:
```bash
aws --endpoint-url=http://localhost:4566 s3 ls
```

### 3. Configure Application

Create `src/main/resources/application-local.yml` (or use default):

```yaml
spring:
  application:
    name: jreg

server:
  port: 8080

aws:
  s3:
    bucket-name: jreg-registry
    endpoint: http://localhost:4566  # LocalStack
    region: us-east-1
    access-key: test  # LocalStack dummy credentials
    secret-key: test

logging:
  level:
    com.jreg: DEBUG
    com.amazonaws: WARN

management:
  endpoints:
    web:
      exposure:
        include: health,metrics,prometheus
  metrics:
    export:
      prometheus:
        enabled: true
```

### 4. Build and Run

```bash
# Build the project
mvn clean package -DskipTests

# Run the application
mvn spring-boot:run -Dspring-boot.run.profiles=local

# Or run the JAR directly
java -jar target/jreg-1.0.0-SNAPSHOT.jar --spring.profiles.active=local
```

The registry should start on `http://localhost:8080`.

### 5. Verify Registry is Running

```bash
# Check API version
curl http://localhost:8080/v2/

# Expected response:
# {"version":"1.0.0"}

# Check health endpoint
curl http://localhost:8080/actuator/health

# Expected response:
# {"status":"UP"}
```

---

## Testing with Docker CLI

### Push an Image

```bash
# Pull a small public image
docker pull busybox:latest

# Tag it for our local registry
docker tag busybox:latest localhost:8080/myorg/busybox:latest

# Push to jreg
docker push localhost:8080/myorg/busybox:latest
```

Expected output:
```
The push refers to repository [localhost:8080/myorg/busybox]
sha256:abc123...: Pushed
latest: digest: sha256:def456... size: 1234
```

### Pull an Image

```bash
# Remove local copy
docker rmi localhost:8080/myorg/busybox:latest

# Pull from jreg
docker pull localhost:8080/myorg/busybox:latest
```

### List Tags

```bash
curl http://localhost:8080/v2/myorg/busybox/tags/list
```

Expected response:
```json
{
  "name": "myorg/busybox",
  "tags": ["latest"]
}
```

---

## Testing with ORAS CLI

[ORAS](https://oras.land/) is a tool for pushing/pulling OCI artifacts (non-container images).

### Install ORAS

```bash
# macOS
brew install oras

# Linux (binary download)
curl -LO https://github.com/oras-project/oras/releases/download/v1.1.0/oras_1.1.0_linux_amd64.tar.gz
tar -xzf oras_1.1.0_linux_amd64.tar.gz
sudo mv oras /usr/local/bin/

# Windows (Chocolatey)
choco install oras
```

### Push an Artifact

```bash
# Create a test file
echo "Hello from jreg" > hello.txt

# Push as OCI artifact
oras push localhost:8080/myorg/artifacts:v1 hello.txt:text/plain
```

### Pull an Artifact

```bash
# Pull and extract
oras pull localhost:8080/myorg/artifacts:v1

# Verify
cat hello.txt
```

---

## Running Tests

### Unit Tests

```bash
mvn test
```

### Integration Tests (with Testcontainers)

```bash
# Requires Docker running
mvn verify
```

Integration tests will:
1. Start LocalStack in a container
2. Start jreg Spring Boot app
3. Run OCI conformance tests using Docker client
4. Verify all endpoints work correctly

### OCI Conformance Tests

```bash
# Install OCI conformance test suite
git clone https://github.com/opencontainers/distribution-spec.git
cd distribution-spec/conformance

# Run against jreg
go test -v -run TestPush ./... \
  -registry=localhost:8080 \
  -repository=conformance/test
```

---

## Development Workflow

### File Structure

```
jreg/
├── src/
│   ├── main/
│   │   ├── java/com/jreg/
│   │   │   ├── controller/      # REST endpoints
│   │   │   ├── service/         # Business logic
│   │   │   ├── storage/         # S3 abstraction
│   │   │   ├── model/           # Domain objects
│   │   │   ├── exception/       # Error handling
│   │   │   └── util/            # Helpers
│   │   └── resources/
│   │       ├── application.yml
│   │       └── application-local.yml
│   └── test/
│       ├── java/com/jreg/
│       │   ├── controller/      # Controller tests
│       │   ├── service/         # Service tests
│       │   └── integration/     # End-to-end tests
│       └── resources/
│           └── test-manifests/  # Sample OCI manifests
├── specs/                       # Specification docs
├── pom.xml                      # Maven dependencies
├── docker-compose.yml           # LocalStack setup
└── README.md
```

### Hot Reload

Use Spring Boot DevTools for automatic restart on code changes:

```xml
<!-- Already included in pom.xml -->
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-devtools</artifactId>
  <scope>runtime</scope>
  <optional>true</optional>
</dependency>
```

Just save your Java files and the app will auto-restart.

### Debugging

**IntelliJ IDEA:**
1. Right-click `JregApplication.java`
2. Select "Debug 'JregApplication'"
3. Set breakpoints in your code

**VS Code:**
1. Install "Debugger for Java" extension
2. Open `JregApplication.java`
3. Press F5 to start debugging

**Command Line:**
```bash
mvn spring-boot:run -Dspring-boot.run.jvmArguments="-Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=5005"
```

Then attach your IDE debugger to port 5005.

---

## Observability

### Logs

jreg uses JSON structured logging (Logback with logstash encoder):

```bash
# Tail application logs
tail -f logs/jreg.log

# Pretty-print JSON logs
tail -f logs/jreg.log | jq .
```

Example log entry:
```json
{
  "timestamp": "2025-10-23T10:00:00.123Z",
  "level": "INFO",
  "logger": "com.jreg.service.BlobService",
  "message": "Blob uploaded successfully",
  "request_id": "uuid-1234",
  "digest": "sha256:abc123...",
  "repository": "myorg/busybox",
  "size_bytes": 1048576
}
```

### Metrics

View Prometheus metrics:

```bash
curl http://localhost:8080/actuator/prometheus
```

Key metrics:
- `http_server_requests_seconds`: Request latency (p50, p95, p99)
- `jreg_blob_uploads_total`: Total blob uploads
- `jreg_blob_upload_bytes_total`: Total bytes uploaded
- `jreg_manifest_pushes_total`: Total manifest pushes
- `jreg_s3_operations_total`: S3 API calls
- `jreg_s3_errors_total`: S3 errors

### Health Checks

```bash
# Overall health
curl http://localhost:8080/actuator/health

# Detailed health (includes S3 connectivity)
curl http://localhost:8080/actuator/health?details=true
```

---

## Common Tasks

### Create S3 Bucket (LocalStack)

```bash
aws --endpoint-url=http://localhost:4566 s3 mb s3://jreg-registry
```

### List S3 Contents

```bash
# List all objects
aws --endpoint-url=http://localhost:4566 s3 ls s3://jreg-registry --recursive

# List blobs only
aws --endpoint-url=http://localhost:4566 s3 ls s3://jreg-registry/blobs/ --recursive

# List manifests for a repository
aws --endpoint-url=http://localhost:4566 s3 ls s3://jreg-registry/manifests/myorg/busybox/
```

### Clean Up S3 Data

```bash
# Delete all objects
aws --endpoint-url=http://localhost:4566 s3 rm s3://jreg-registry --recursive

# Delete bucket
aws --endpoint-url=http://localhost:4566 s3 rb s3://jreg-registry
```

### Generate OpenAPI Docs

```bash
# Start the app, then:
curl http://localhost:8080/v3/api-docs > openapi-generated.json

# Or view Swagger UI
open http://localhost:8080/swagger-ui.html
```

---

## Troubleshooting

### Port Already in Use

If port 8080 is busy:

```bash
# Change port in application-local.yml
server:
  port: 8081

# Or use environment variable
SERVER_PORT=8081 mvn spring-boot:run
```

### LocalStack Connection Issues

```bash
# Check if LocalStack is running
docker ps | grep localstack

# Check LocalStack logs
docker logs jreg-localstack

# Restart LocalStack
docker restart jreg-localstack
```

### Docker Push Fails with "manifest invalid"

This usually means a referenced blob doesn't exist. Ensure layers are pushed before the manifest:

```bash
# Check blob exists
curl -I http://localhost:8080/v2/myorg/busybox/blobs/sha256:abc123...

# Should return 200 OK
```

### Tests Fail with "Testcontainers not found"

Ensure Docker is running:

```bash
docker ps

# If Docker isn't running, start Docker Desktop
```

### "NoSuchKeyException" from S3

Verify S3 bucket exists:

```bash
aws --endpoint-url=http://localhost:4566 s3 ls

# Create if missing
aws --endpoint-url=http://localhost:4566 s3 mb s3://jreg-registry
```

---

## Next Steps

- **Read the Spec**: See `specs/001-oci-registry-server/spec.md` for requirements
- **Review Data Model**: See `specs/001-oci-registry-server/data-model.md` for entity definitions
- **Check API Contract**: See `specs/001-oci-registry-server/contracts/openapi.yaml` for endpoint details
- **Implement Features**: Follow tasks in `specs/001-oci-registry-server/tasks.md` (once generated)
- **Deploy to Production**: Use real AWS S3 (see deployment guide)

---

## Production Deployment (Preview)

For production, you'll need:

1. **AWS S3 Bucket**: Real S3 with versioning enabled
2. **IAM Credentials**: For S3 access (GetObject, PutObject, DeleteObject, ListBucket)
3. **Load Balancer**: HTTPS termination (ALB or Nginx)
4. **Authentication**: Add auth layer (OAuth2, JWT, or registry token auth)
5. **Monitoring**: CloudWatch, Prometheus, Grafana

Quick production config:

```yaml
# application-prod.yml
aws:
  s3:
    bucket-name: jreg-prod-registry
    region: us-west-2
    # Use IAM role (no access-key/secret-key)

server:
  port: 8080
  forward-headers-strategy: framework  # Trust X-Forwarded-* headers

logging:
  level:
    com.jreg: INFO
```

Run with:
```bash
java -jar jreg-1.0.0.jar --spring.profiles.active=prod
```

---

## Resources

- **OCI Distribution Spec**: https://github.com/opencontainers/distribution-spec
- **OCI Image Spec**: https://github.com/opencontainers/image-spec
- **Spring Boot Docs**: https://spring.io/projects/spring-boot
- **AWS SDK for Java**: https://aws.amazon.com/sdk-for-java/
- **LocalStack Docs**: https://docs.localstack.cloud/
- **Docker Registry API**: https://docs.docker.com/registry/spec/api/

---

## Getting Help

- **GitHub Issues**: https://github.com/yourusername/jreg/issues
- **Documentation**: See `docs/` folder
- **Contributing**: See `CONTRIBUTING.md`

Happy hacking! 🚀
