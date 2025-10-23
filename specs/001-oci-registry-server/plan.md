# Implementation Plan: OCI-Compliant Registry Server (jreg)

**Branch**: `001-oci-registry-server` | **Date**: 2025-10-23 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/001-oci-registry-server/spec.md`

**Note**: This template is filled in by the `/speckit.plan` command. See `.specify/templates/commands/plan.md` for the execution workflow.

## Summary

Build a production-grade OCI-compliant container registry server named "jreg" that implements the full OCI Distribution Specification v1.1.1. The registry will enable Docker, containerd, ORAS, and other OCI clients to push and pull container images and artifacts. The system will use Java for server implementation with AWS S3 as the content-addressable storage backend for blobs and manifests, eliminating the need for a traditional database. Key capabilities include chunked blob uploads, content deduplication, referrers API for artifact relationships, tag management, and full OCI conformance test compliance.

## Technical Context

**Language/Version**: Java 21 (LTS) with virtual threads for high-concurrency performance  
**Primary Dependencies**: Spring Boot 3.2+ (web framework), AWS SDK for Java 2.x (S3 integration), Jackson (JSON processing), Micrometer (metrics), SLF4J + Logback (logging)  
**Storage**: AWS S3 (content-addressable blob storage, manifest storage, metadata storage) - no SQL/NoSQL database required  
**Testing**: JUnit 5, Mockito, Testcontainers (S3 mock via LocalStack), OCI Distribution Spec conformance test suite  
**Target Platform**: Linux server (containerized deployment via Docker/Kubernetes)  
**Project Type**: Single server application (RESTful API service)  
**Performance Goals**: 100+ concurrent push/pull operations, <200ms p95 response time for manifest operations, <10s for 100MB image on local network, support chunked uploads for large blobs  
**Constraints**: OCI Distribution Spec v1.1.1 compliance (strict), OCI Image Spec v1.1.1 compatibility, no database dependency, S3 eventual consistency handling, support HTTP Range requests  
**Scale/Scope**: Single-instance deployment initially, designed for horizontal scaling, support for thousands of repositories, millions of blobs, S3 lifecycle policies for storage management

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

### I. API-First Design ✅
- **Status**: PASS
- **Evidence**: OCI Distribution Spec v1.1.1 provides complete OpenAPI-compatible contract definitions for all endpoints. Contracts will be documented before implementation.
- **Actions**: Generate OpenAPI 3.0 specification in Phase 1 based on OCI spec endpoints (end-1 through end-13)

### II. Test-Driven Development (NON-NEGOTIABLE) ✅
- **Status**: PASS (commitment required)
- **Evidence**: Plan includes comprehensive test strategy - contract tests for all 13 OCI endpoints, integration tests with Docker/ORAS clients, unit tests for business logic
- **Actions**: 
  - Write contract tests for each endpoint before implementation
  - Use OCI conformance test suite as acceptance criteria
  - Target 80%+ unit test coverage
  - Testcontainers with LocalStack for S3 integration testing

### III. Observability & Monitoring ✅
- **Status**: PASS
- **Evidence**: Plan includes structured logging (SLF4J/Logback JSON), Prometheus metrics via Micrometer, health endpoints
- **Actions**:
  - Implement `/health` and `/ready` endpoints per constitution
  - Add request ID (X-Request-ID) to all responses for tracing
  - Log all blob/manifest operations with digest and repository context
  - Expose metrics: request rate, latency (p50/p95/p99), error rate, S3 operation latency

### IV. Performance & Scalability ✅
- **Status**: PASS
- **Evidence**: Performance goals defined (100+ concurrent ops, <200ms p95). Java 21 virtual threads enable high concurrency. S3 provides horizontal scalability.
- **Actions**:
  - Use virtual threads for all I/O operations
  - Implement connection pooling for S3 client
  - Add performance tests for blob upload/download under load
  - Cache manifest lookups with short TTL (optional optimization)

### V. Security by Default ⚠️
- **Status**: DEFERRED (authentication implementation out of scope for MVP)
- **Evidence**: Endpoints will support authentication hooks, but specific auth mechanism (JWT, OAuth2, token) not implemented in Phase 1
- **Actions**:
  - Design endpoints with authentication filter/interceptor architecture
  - Validate all inputs (repository names, tags, digests against OCI regex patterns)
  - Implement HTTPS support via reverse proxy recommendation
  - Document auth integration points for future implementation

### Quality Gates Compliance ✅
- **Status**: PASS
- **Evidence**: 
  - Tests: JUnit 5 + Mockito + Testcontainers + OCI conformance suite
  - Security: Dependency scanning via Maven/Gradle, input validation
  - API Contract: OpenAPI spec generation planned
  - Linting: Checkstyle/SpotBugs configuration
  - Performance: Load tests for critical paths
  - Documentation: OpenAPI docs, README, quickstart guide

**Overall Gate Status**: PASS with authentication deferred to post-MVP (documented as assumption in spec)

## Project Structure

### Documentation (this feature)

```text
specs/001-oci-registry-server/
├── plan.md              # This file (/speckit.plan command output)
├── research.md          # Phase 0 output (/speckit.plan command)
├── data-model.md        # Phase 1 output (/speckit.plan command)
├── quickstart.md        # Phase 1 output (/speckit.plan command)
├── contracts/           # Phase 1 output (/speckit.plan command)
│   └── openapi.yaml    # OCI Distribution API OpenAPI 3.0 spec
└── tasks.md             # Phase 2 output (/speckit.tasks command - NOT created by /speckit.plan)
```

### Source Code (repository root)

```text
src/
├── main/
│   ├── java/
│   │   └── com/
│   │       └── jreg/
│   │           ├── JregApplication.java          # Spring Boot main class
│   │           ├── config/                       # Configuration classes
│   │           │   ├── S3Config.java            # AWS S3 client configuration
│   │           │   ├── WebConfig.java           # Web/REST configuration
│   │           │   └── MetricsConfig.java       # Micrometer metrics setup
│   │           ├── controller/                   # REST controllers (OCI endpoints)
│   │           │   ├── VersionController.java   # GET /v2/
│   │           │   ├── ManifestController.java  # Manifest operations
│   │           │   ├── BlobController.java      # Blob operations
│   │           │   ├── UploadController.java    # Chunked upload sessions
│   │           │   ├── TagController.java       # Tag listing
│   │           │   ├── ReferrersController.java # Referrers API
│   │           │   └── HealthController.java    # Health/readiness checks
│   │           ├── service/                      # Business logic
│   │           │   ├── ManifestService.java     # Manifest CRUD operations
│   │           │   ├── BlobService.java         # Blob storage operations
│   │           │   ├── UploadSessionService.java # Chunked upload management
│   │           │   ├── TagService.java          # Tag management
│   │           │   ├── ReferrersService.java    # Referrers tracking
│   │           │   └── ValidationService.java   # Input validation (names, digests)
│   │           ├── storage/                      # Storage abstraction layer
│   │           │   ├── StorageBackend.java      # Interface for storage operations
│   │           │   ├── S3StorageBackend.java    # S3 implementation
│   │           │   └── UploadSession.java       # Upload session state
│   │           ├── model/                        # Domain models
│   │           │   ├── Manifest.java            # Manifest representation
│   │           │   ├── Blob.java                # Blob metadata
│   │           │   ├── Repository.java          # Repository metadata
│   │           │   ├── Tag.java                 # Tag representation
│   │           │   ├── Digest.java              # Digest value object
│   │           │   └── OciError.java            # OCI error response format
│   │           ├── exception/                    # Custom exceptions
│   │           │   ├── OciException.java        # Base OCI exception
│   │           │   ├── ManifestNotFoundException.java
│   │           │   ├── BlobNotFoundException.java
│   │           │   ├── InvalidDigestException.java
│   │           │   └── InvalidNameException.java
│   │           └── util/                         # Utility classes
│   │               ├── DigestCalculator.java    # SHA256 digest computation
│   │               ├── RegexValidator.java      # OCI name/tag validation
│   │               └── S3KeyGenerator.java      # S3 key path generation
│   └── resources/
│       ├── application.yml                       # Spring Boot configuration
│       ├── logback-spring.xml                    # Logging configuration (JSON)
│       └── static/
│           └── openapi.yaml                      # API documentation
│
└── test/
    ├── java/
    │   └── com/
    │       └── jreg/
    │           ├── contract/                     # API contract tests
    │           │   ├── ManifestContractTest.java
    │           │   ├── BlobContractTest.java
    │           │   ├── UploadContractTest.java
    │           │   └── OciConformanceTest.java  # OCI spec conformance
    │           ├── integration/                  # Integration tests
    │           │   ├── DockerPushPullTest.java  # End-to-end with Docker
    │           │   ├── S3IntegrationTest.java   # S3 operations with LocalStack
    │           │   └── ChunkedUploadTest.java   # Chunked upload flow
    │           └── unit/                         # Unit tests
    │               ├── service/
    │               ├── storage/
    │               └── util/
    └── resources/
        └── application-test.yml                  # Test configuration

pom.xml                                           # Maven build configuration
Dockerfile                                        # Container image definition
docker-compose.yml                                # Local development setup
README.md                                         # Project documentation
.gitignore                                        # Git ignore rules
```

**Structure Decision**: Single Java project using Spring Boot framework. The architecture follows a layered approach:
- **Controller layer**: REST endpoints implementing OCI Distribution API
- **Service layer**: Business logic for manifest/blob/tag management
- **Storage layer**: Abstraction over AWS S3 for content-addressable storage
- **Model layer**: Domain objects representing OCI entities (manifests, blobs, digests)

S3 is used as the sole storage backend with a key structure:
- Blobs: `blobs/<algorithm>/<first-2-chars>/<rest-of-digest>`
- Manifests: `manifests/<repository>/<algorithm>/<digest>`
- Tags: `tags/<repository>/<tag-name>` (points to manifest digest)
- Upload sessions: `uploads/<session-id>/metadata.json` and `/chunks/`

No traditional database needed - S3 object metadata and key hierarchy provide sufficient query capabilities for OCI operations.

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

**No violations to track** - All constitution principles pass or have documented deferral with clear rationale (authentication deferred to post-MVP).

---

## Phase Completion Summary

### Phase 0: Research ✅ COMPLETE
**Deliverables**:
- ✅ `research.md` - 12 architectural decision areas documented with rationales and alternatives considered
- ✅ All unknowns resolved (S3-only architecture, virtual threads, chunked uploads, referrers indexing)

### Phase 1: Design ✅ COMPLETE
**Deliverables**:
- ✅ `data-model.md` - 9 core entities defined (Repository, Manifest, Blob, Tag, Digest, UploadSession, ReferrersIndex, OciError, ByteRange)
- ✅ `contracts/openapi.yaml` - OpenAPI 3.0 specification for all 13 OCI Distribution endpoints
- ✅ `quickstart.md` - Local development guide with prerequisites, setup, Docker/ORAS testing, troubleshooting
- ✅ Agent context updated - Copilot instructions file created with Java 21/Spring Boot/AWS SDK stack

**Constitution Re-Check**: ✅ PASS
- All Phase 1 artifacts align with constitution principles
- OpenAPI contract satisfies API-First Design
- Test strategy documented (contract/integration/unit tests) for TDD principle
- Observability requirements captured (JSON logs, Prometheus metrics, health endpoints)
- Performance considerations documented (virtual threads, connection pooling, p95 targets)
- Security validation rules defined (regex patterns, digest verification, input sanitization)

### Phase 2: Task Breakdown - PENDING
**Next Command**: `/speckit.tasks` to generate `tasks.md`
- Break down implementation into concrete tasks organized by user story (P1: Push, P2: Pull, P3: Discovery, P4: Lifecycle)
- Specify file paths, TDD requirements, parallelization opportunities
- Create dependency graph for task execution order
