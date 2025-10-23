# jreg - OCI-Compliant Container Registry

An OCI-compliant container registry server implementing the OCI Distribution Specification v1.1.1.

## Features

- Full OCI Distribution Spec v1.1.1 compliance
- Docker and ORAS client support
- AWS S3 storage backend (no database required)
- Chunked blob uploads with resume support
- Multi-architecture image support
- Referrers API for artifact relationships
- Tag management and discovery

## Quick Start

See [quickstart.md](specs/001-oci-registry-server/quickstart.md) for detailed setup instructions.

### Prerequisites

- Java 21
- Maven 3.9+
- Docker 20.10+
- AWS CLI (optional, for production)

### Local Development

```bash
# Start LocalStack (S3 mock)
docker compose up -d localstack

# Build and run
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

### Test with Docker

```bash
# Push an image
docker pull busybox:latest
docker tag busybox:latest localhost:8080/myrepo/busybox:latest
docker push localhost:8080/myrepo/busybox:latest

# Pull an image
docker pull localhost:8080/myrepo/busybox:latest
```

## Documentation

- [Feature Specification](specs/001-oci-registry-server/spec.md)
- [Implementation Plan](specs/001-oci-registry-server/plan.md)
- [Data Model](specs/001-oci-registry-server/data-model.md)
- [API Contracts](specs/001-oci-registry-server/contracts/openapi.yaml)
- [Quick Start Guide](specs/001-oci-registry-server/quickstart.md)

## Architecture

jreg uses a layered architecture:

- **Controllers**: REST endpoints implementing OCI Distribution API
- **Services**: Business logic for manifest/blob/tag management
- **Storage**: AWS S3 abstraction layer
- **Models**: Domain objects representing OCI entities

Storage is entirely in S3 with no database required. Blobs and manifests are content-addressable using SHA-256 digests.

## License

MIT
