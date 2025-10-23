# Research: OCI Registry Implementation with Java + AWS S3

**Feature**: OCI-Compliant Registry Server (jreg)  
**Date**: 2025-10-23  
**Status**: Complete

## Executive Summary

This research document provides technical decisions and architectural patterns for implementing an OCI Distribution Spec v1.1.1 compliant container registry using Java 21 and AWS S3 as the storage backend. Key findings support a database-free architecture leveraging S3's content-addressable storage capabilities, Spring Boot for API implementation, and virtual threads for high-concurrency performance.

---

## 1. Storage Architecture: S3 Without Database

### Decision

Use AWS S3 as the sole storage backend for blobs, manifests, tags, and upload session metadata. No SQL or NoSQL database required.

### Rationale

**Content-Addressable Storage**: OCI registry operations are primarily keyed by digest (SHA256 hashes), which maps naturally to S3's key-value model. S3 objects can be organized with a hierarchical key structure that enables efficient lookups by digest or repository name.

**S3 Capabilities Match Registry Needs**:
- **Blob storage**: Immutable blobs stored with digest as key - perfect fit for S3
- **Manifest storage**: JSON documents stored with digest-based keys
- **Tag management**: Small pointer files (tag → manifest digest) stored as S3 objects
- **Upload sessions**: Temporary state stored in S3 with TTL via lifecycle policies
- **Metadata**: S3 object metadata (Content-Type, Content-Length, custom headers) sufficient for OCI requirements

**Operational Benefits**:
- No database to provision, patch, or scale
- S3 provides 99.999999999% durability automatically
- Pay-per-use pricing (no database instance costs)
- Simplified deployment (one less service to manage)
- S3 lifecycle policies handle cleanup of abandoned uploads

**Performance Characteristics**:
- S3 GET/HEAD operations: ~10-20ms latency (acceptable for p95 <200ms target)
- S3 supports HTTP Range requests natively (OCI requirement)
- Multipart uploads for chunked blob uploads
- S3 Transfer Acceleration for cross-region performance

### S3 Key Structure

```
s3://jreg-registry-bucket/
├── blobs/                          # Blob storage (content-addressable)
│   └── sha256/
│       ├── ab/                     # First 2 chars of digest
│       │   └── abcd1234...         # Full digest (blob content)
│       └── cd/
│           └── cdef5678...
├── manifests/                      # Manifest storage by repository
│   └── <repository-name>/
│       └── sha256/
│           └── <digest>            # Manifest JSON
├── tags/                           # Tag pointers to manifests
│   └── <repository-name>/
│       └── <tag-name>              # Contains manifest digest
├── repositories/                   # Repository metadata (optional)
│   └── <repository-name>/
│       └── _metadata.json          # Creation time, stats
└── uploads/                        # Temporary upload sessions
    └── <session-uuid>/
        ├── metadata.json           # Session state (repo, content-type, size)
        └── chunks/                 # Uploaded chunks
            ├── 0-1048575           # Byte range chunk
            └── 1048576-2097151
```

### Alternatives Considered

**PostgreSQL/MySQL + S3**:
- **Pros**: Transactional consistency, complex queries, familiar tooling
- **Rejected**: Adds operational complexity. OCI operations don't require ACID transactions or complex joins. Registry metadata is simple (digest → content mappings).

**MongoDB/DynamoDB + S3**:
- **Pros**: Document model fits manifest JSON, better scalability than RDBMS
- **Rejected**: Still adds a separate service. S3 object metadata + key hierarchy provide sufficient query capabilities for registry operations (lookup by digest, list tags).

**S3 with DynamoDB for metadata index**:
- **Pros**: Fast tag listings, referrer lookups
- **Rejected**: Premature optimization. S3 LIST operations with prefix filtering (e.g., `tags/<repo>/`) are acceptable for MVP. Can add caching layer if needed.

### S3 Configuration Best Practices

**Bucket Settings**:
- Enable versioning (optional - for disaster recovery)
- Enable server-side encryption (AES-256 or KMS)
- Configure lifecycle policies: delete uploads older than 7 days
- Use S3 Standard storage class (optimize costs later with S3 Intelligent-Tiering)

**IAM Permissions Required**:
- `s3:GetObject`, `s3:PutObject`, `s3:DeleteObject`
- `s3:ListBucket` with prefix condition
- `s3:GetObjectMetadata`, `s3:HeadObject`

**Consistency Model**:
- S3 provides strong read-after-write consistency (as of Dec 2020)
- Safe to immediately read after PUT operations
- No eventual consistency concerns for registry operations

---

## 2. Java 21 + Spring Boot Framework

### Decision

Use Java 21 LTS with Spring Boot 3.2+ as the web framework.

### Rationale

**Java 21 Benefits**:
- **Virtual Threads (Project Loom)**: Handle 100+ concurrent requests without thread pool exhaustion. Each S3 I/O operation runs on a lightweight virtual thread.
- **Pattern Matching**: Cleaner code for parsing OCI manifest variants (Image Manifest vs Image Index)
- **Records**: Perfect for immutable domain models (Digest, Tag, etc.)
- **LTS Support**: Long-term support until 2029

**Spring Boot Advantages**:
- **Rapid Development**: Auto-configuration for web server, JSON parsing, metrics, logging
- **Production-Ready**: Built-in health checks, metrics (Micrometer), graceful shutdown
- **AWS Integration**: Spring Cloud AWS provides S3 client autoconfiguration
- **Testing Support**: MockMvc for controller tests, TestRestTemplate for integration tests
- **Ecosystem**: Extensive documentation, community support, middleware libraries

**Performance Profile**:
- Spring Boot with virtual threads: 10k+ concurrent connections on modest hardware
- Jackson JSON parsing: Fast enough for manifest processing (<1ms for typical manifests)
- Spring Web MVC: Sufficient for OCI API (no need for reactive stack complexity)

### Spring Boot Dependencies

```xml
<dependencies>
    <!-- Spring Boot Starter -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
        <version>3.2.0</version>
    </dependency>
    
    <!-- AWS SDK for Java 2.x -->
    <dependency>
        <groupId>software.amazon.awssdk</groupId>
        <artifactId>s3</artifactId>
        <version>2.20.0</version>
    </dependency>
    
    <!-- Micrometer for Prometheus metrics -->
    <dependency>
        <groupId>io.micrometer</groupId>
        <artifactId>micrometer-registry-prometheus</artifactId>
    </dependency>
    
    <!-- Validation -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-validation</artifactId>
    </dependency>
    
    <!-- Testing -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-test</artifactId>
        <scope>test</scope>
    </dependency>
    
    <dependency>
        <groupId>org.testcontainers</groupId>
        <artifactId>localstack</artifactId>
        <version>1.19.0</version>
        <scope>test</scope>
    </dependency>
</dependencies>
```

### Alternatives Considered

**Quarkus**:
- **Pros**: Faster startup, lower memory, native compilation
- **Rejected**: Less mature AWS SDK integration. Spring Boot's ecosystem and production tooling outweigh startup time benefits for a registry server.

**Micronaut**:
- **Pros**: Compile-time DI, low memory footprint
- **Rejected**: Smaller community, less AWS integration maturity. Spring Boot's stability and ecosystem preferred.

**Plain Jakarta EE**:
- **Pros**: Standard, no Spring magic
- **Rejected**: More boilerplate, less tooling for metrics/health checks. Spring Boot auto-configuration saves development time.

---

## 3. Chunked Upload Session Management

### Decision

Store upload session metadata in S3 as JSON objects with a TTL-based cleanup via S3 lifecycle policies. Track uploaded byte ranges in memory during session and persist to S3 metadata after each chunk.

### Rationale

**Session State Requirements**:
- Track repository name, upload UUID, content-type
- Record uploaded byte ranges (for sequential chunk validation)
- Support resumption (GET /v2/<name>/blobs/uploads/<reference>)
- Expire abandoned sessions (< 7 days)

**S3-Based Implementation**:
- Session created → PUT `uploads/<uuid>/metadata.json` with initial state
- Chunk uploaded → Append chunk to `uploads/<uuid>/chunks/<start>-<end>`
- Session queried → GET metadata.json, return Range header with last byte
- Session finalized → Validate chunks, concatenate into final blob, move to `blobs/`
- Cleanup → S3 lifecycle policy deletes `uploads/` objects older than 7 days

**In-Memory State** (optional optimization):
- Cache active sessions in memory (ConcurrentHashMap)
- Evict on finalization or after 1 hour of inactivity
- Fallback to S3 if not in cache

### Upload Flow

1. **POST /v2/<name>/blobs/uploads/** → Generate UUID, create metadata.json, return Location header
2. **PATCH /v2/<name>/blobs/uploads/<uuid>** → Append chunk to S3, update metadata
3. **PUT /v2/<name>/blobs/uploads/<uuid>?digest=sha256:...** → Finalize, compute digest, move to blobs/
4. **GET /v2/<name>/blobs/uploads/<uuid>** (resumption) → Return Range: 0-<last-byte>

### S3 Multipart Upload

For large blobs (>5MB), use S3 Multipart Upload API:
- Initiate multipart upload → Get upload ID
- Upload parts (5MB chunks) → Get ETags
- Complete multipart upload → Provide list of part ETags

**Benefits**: S3 handles assembly, retries individual parts, better performance for large blobs.

### Alternatives Considered

**Redis for Session State**:
- **Pros**: Fast access, built-in TTL
- **Rejected**: Adds another service dependency. S3 metadata sufficient, can add Redis later if session query latency becomes issue.

**File System**:
- **Pros**: Simple, fast local I/O
- **Rejected**: Not suitable for multi-instance deployment, no durability guarantees, requires shared file system (NFS/EFS) which adds complexity.

---

## 4. Tag Listing and Pagination

### Decision

Store tags as individual S3 objects under `tags/<repository>/<tag-name>`. For tag listing, use S3 ListObjectsV2 with prefix filtering and implement lexical sorting + pagination in application code.

### Rationale

**Tag Storage**:
- Each tag is a small S3 object (< 1KB) containing manifest digest
- Object key: `tags/<repository>/<tag-name>`
- Object content: JSON `{"digest": "sha256:abc123...", "updated_at": "2025-10-23T..."}`

**Listing Implementation**:
- S3 ListObjectsV2: `prefix=tags/<repository>/`, `max-keys=1000`
- Extract tag names from keys
- Sort lexically in application (S3 returns lexical order by key)
- Implement pagination with `last` parameter (continuation token)

**Performance**:
- S3 LIST operation: ~50-100ms for 1000 keys
- Acceptable for tag listings (not a hot path)
- Cache entire tag list in memory if repository has <10k tags (optional optimization)

### OCI Pagination Requirements

- `/v2/<name>/tags/list?n=<int>` → Return up to `n` tags
- `/v2/<name>/tags/list?n=<int>&last=<tagname>` → Return tags after `last`
- Include `Link: <url>; rel="next"` header when more tags exist

**Implementation**:
- Query S3 with higher limit (n * 2) to determine if more exist
- Return first `n` tags
- If more exist, generate Link header with `last=<nth-tag>`

### Alternatives Considered

**DynamoDB Secondary Index**:
- **Pros**: Fast pagination, native sorting
- **Rejected**: Adds database complexity. TAG listing not a critical performance path.

**S3 Select**:
- **Pros**: Server-side filtering
- **Rejected**: Requires storing tags in a single file (e.g., tags.json per repo). Makes concurrent tag updates complex.

---

## 5. Referrers API Implementation

### Decision

Implement referrers API by storing a referrers index file per manifest digest. When a manifest with `subject` field is pushed, update the index file at `referrers/<subject-digest>.json`.

### Rationale

**Referrers Requirement**:
- OCI spec requires `/v2/<name>/referrers/<digest>` to return all manifests with `subject` field pointing to `<digest>`
- Must support filtering by `artifactType`
- Must return OCI Image Index JSON format

**Index File Approach**:
- File path: `referrers/<repository>/<sha256>/<digest>.json`
- File content: OCI Image Index with `manifests[]` array
- On manifest push with `subject`: append descriptor to index file
- On manifest delete: remove descriptor from index file

**Concurrent Updates**:
- Use S3 conditional PUT (If-Match ETag) to prevent lost updates
- Retry on version conflict (exponential backoff)
- Acceptable because referrer updates are rare

**Filtering**:
- Load index file, filter `manifests[]` by `artifactType` in application code
- Return filtered results with `OCI-Filters-Applied: artifactType` header

### Fallback to Referrers Tag Schema

If referrers API returns 404, clients fall back to referrers tag schema:
- Tag format: `sha256-<digest>` (e.g., `sha256-abcd1234...`)
- Same Image Index content stored as a regular tagged manifest

**Implementation Strategy**:
- Start with referrers tag schema (store referrers index as tagged manifest)
- Later migrate to dedicated referrers API endpoint (read from both during transition)

### Alternatives Considered

**Database Table for Referrers**:
- **Pros**: Fast queries, easy filtering
- **Rejected**: Breaks database-free architecture. Index file approach works for MVP.

**No Index (Scan All Manifests)**:
- **Pros**: No index maintenance
- **Rejected**: Unacceptable performance. Would require listing all manifests in repository and parsing each to check `subject` field.

---

## 6. Digest Validation and Content Integrity

### Decision

Compute SHA256 digest on-the-fly during blob/manifest upload and validate against client-provided digest. Reject uploads with mismatched digests (400 Bad Request with DIGEST_INVALID error code).

### Rationale

**OCI Requirement**:
- Clients provide expected digest in query parameter (`?digest=sha256:...`)
- Registry MUST validate digest matches uploaded content
- Return `Docker-Content-Digest` header with canonical digest

**Implementation**:
- Use Java `MessageDigest` with SHA-256 algorithm
- Stream content through digest calculator during upload
- Compare computed digest with provided digest
- Store blob with digest-based key only if validation passes

**Performance Impact**:
- Negligible - SHA-256 computation is fast (~500 MB/s on modern CPUs)
- Streaming approach avoids memory overhead

### SHA-256 Code Example

```java
public class DigestCalculator {
    public static Digest calculate(InputStream input) throws IOException {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] buffer = new byte[8192];
        int bytesRead;
        
        while ((bytesRead = input.read(buffer)) != -1) {
            md.update(buffer, 0, bytesRead);
        }
        
        byte[] hash = md.digest();
        String hex = Hex.encodeHexString(hash);
        return new Digest("sha256", hex);
    }
}
```

### Alternatives Considered

**Trust Client Digest**:
- **Pros**: Faster (no validation)
- **Rejected**: Security risk. Malicious clients could upload incorrect content. OCI spec requires validation.

**Validate Only on Finalization**:
- **Pros**: Simpler for chunked uploads
- **Rejected**: Late validation means wasted storage for invalid uploads. Better to validate each chunk's contribution.

---

## 7. HTTP Range Request Support for Blobs

### Decision

Leverage S3's native HTTP Range request support. Forward Range header from client to S3 GetObject API, return partial content with 206 status.

### Rationale

**OCI Requirement**:
- Registry SHOULD support Range requests for blob downloads (resumable pulls)
- Return 206 Partial Content with Content-Range header

**Implementation**:
- Check for `Range: bytes=<start>-<end>` header in blob GET request
- Pass Range to S3 GetObject request
- S3 returns partial content automatically
- Proxy response to client with 206 status and appropriate headers

**S3 Support**:
- S3 natively supports Range requests (RFC 7233)
- Handles multi-range, open-ended ranges, suffix ranges
- No additional code needed beyond forwarding header

### Example Flow

```
Client: GET /v2/myrepo/blobs/sha256:abc123...
        Range: bytes=1000-1999
        
Registry: Forward to S3 with Range header

S3: Returns bytes 1000-1999 with headers:
    Content-Range: bytes 1000-1999/50000
    Content-Length: 1000
    
Registry: Proxy response with 206 status
```

---

## 8. Repository Name and Tag Validation

### Decision

Pre-compile regex patterns from OCI spec and validate all repository names and tags on ingress. Reject invalid names with 400 Bad Request and NAME_INVALID error code.

### Rationale

**OCI Validation Rules**:
- **Repository name**: `[a-z0-9]+((\.|_|__|-+)[a-z0-9]+)*(\/[a-z0-9]+((\.|_|__|-+)[a-z0-9]+)*)*`
- **Tag name**: `[a-zA-Z0-9_][a-zA-Z0-9._-]{0,127}` (max 128 chars)
- **Digest**: `<algorithm>:<hex>` (e.g., `sha256:abcd...`)

**Implementation**:
```java
public class RegexValidator {
    private static final Pattern REPO_PATTERN = Pattern.compile(
        "[a-z0-9]+((\\.| _|__|-+)[a-z0-9]+)*(\\/[a-z0-9]+((\\.| _|__|-+)[a-z0-9]+)*)*"
    );
    
    private static final Pattern TAG_PATTERN = Pattern.compile(
        "[a-zA-Z0-9_][a-zA-Z0-9._-]{0,127}"
    );
    
    public static void validateRepository(String name) {
        if (!REPO_PATTERN.matcher(name).matches()) {
            throw new InvalidNameException("Repository name invalid");
        }
    }
}
```

**Validation Points**:
- Controller layer (before processing request)
- Early rejection prevents downstream errors
- Clear error messages guide client developers

---

## 9. Error Handling and OCI Error Codes

### Decision

Implement Spring Boot `@ControllerAdvice` exception handler that maps custom exceptions to OCI-compliant JSON error responses with appropriate HTTP status codes.

### Rationale

**OCI Error Format**:
```json
{
  "errors": [
    {
      "code": "BLOB_UNKNOWN",
      "message": "Blob not found",
      "detail": {"digest": "sha256:abc123..."}
    }
  ]
}
```

**Error Code Mapping**:
- `BLOB_UNKNOWN` → 404 (blob not found)
- `MANIFEST_UNKNOWN` → 404 (manifest not found)
- `MANIFEST_INVALID` → 400 (invalid manifest JSON)
- `DIGEST_INVALID` → 400 (digest mismatch)
- `NAME_INVALID` → 400 (invalid repo/tag name)
- `UNAUTHORIZED` → 401 (auth required)
- `DENIED` → 403 (access forbidden)
- `UNSUPPORTED` → 400 (unsupported media type)

**Implementation**:
```java
@ControllerAdvice
public class OciExceptionHandler {
    @ExceptionHandler(BlobNotFoundException.class)
    public ResponseEntity<OciErrorResponse> handleBlobNotFound(BlobNotFoundException ex) {
        OciError error = new OciError("BLOB_UNKNOWN", ex.getMessage());
        return ResponseEntity.status(404).body(new OciErrorResponse(List.of(error)));
    }
}
```

---

## 10. Observability: Logging, Metrics, Health Checks

### Decision

- **Logging**: Logback with JSON format, include request ID (X-Request-ID) in MDC
- **Metrics**: Micrometer with Prometheus registry, expose `/actuator/metrics` and `/actuator/prometheus`
- **Health**: Spring Boot Actuator health checks at `/health` (liveness) and `/ready` (readiness)

### Rationale

**Structured Logging**:
```json
{
  "timestamp": "2025-10-23T10:15:30.123Z",
  "level": "INFO",
  "service": "jreg",
  "request_id": "abc-123",
  "message": "Blob uploaded",
  "digest": "sha256:abc123...",
  "repository": "myrepo",
  "size_bytes": 1048576
}
```

**Metrics to Track**:
- HTTP request rate (by endpoint, status code)
- Latency histograms (p50, p95, p99)
- S3 operation count and latency
- Blob upload/download throughput
- Active upload sessions count
- Error rate by error code

**Health Checks**:
- `/health` → Liveness (always returns 200 unless app crashed)
- `/ready` → Readiness (check S3 connectivity with HeadBucket)

**Implementation**:
- Add Micrometer dependency
- Use `@Timed` annotations on controller methods
- Custom metrics with `MeterRegistry.counter()` and `.timer()`
- Configure logback-spring.xml for JSON output

---

## 11. Testing Strategy

### Decision

Three-tier testing approach:
1. **Unit tests**: Service and utility classes with mocked dependencies (JUnit 5, Mockito)
2. **Contract tests**: Controller endpoints with MockMvc, validate OCI spec compliance
3. **Integration tests**: Full flow with Testcontainers (LocalStack for S3) + Docker client

### Rationale

**Unit Tests** (80% coverage target):
- Fast feedback loop
- Test business logic in isolation
- Mock S3StorageBackend interface

**Contract Tests**:
- Validate HTTP status codes, headers, response formats
- Test each OCI endpoint (end-1 through end-13)
- Ensure compliance with OCI spec requirements
- Use MockMvc to avoid full server startup

**Integration Tests**:
- Real S3 operations via LocalStack (Testcontainers)
- End-to-end flows: docker push → docker pull
- ORAS CLI integration tests
- Chunked upload edge cases

**OCI Conformance Tests**:
- Run official OCI Distribution Spec conformance test suite
- Automated in CI pipeline
- Must pass 100% for Pull, Push, Content Discovery categories

### Test Structure

```
test/
├── unit/
│   ├── ManifestServiceTest.java
│   ├── BlobServiceTest.java
│   └── DigestCalculatorTest.java
├── contract/
│   ├── ManifestEndpointTest.java
│   ├── BlobEndpointTest.java
│   └── OciErrorResponseTest.java
└── integration/
    ├── DockerPushPullIntegrationTest.java
    ├── S3StorageIntegrationTest.java (with LocalStack)
    └── ChunkedUploadIntegrationTest.java
```

---

## 12. Deployment and Configuration

### Decision

Deploy as a containerized Spring Boot application with externalized configuration via environment variables and application.yml.

### Configuration Parameters

```yaml
jreg:
  storage:
    s3:
      bucket-name: ${S3_BUCKET_NAME:jreg-registry}
      region: ${AWS_REGION:us-east-1}
      endpoint: ${S3_ENDPOINT:}  # For LocalStack/MinIO
  upload:
    session-ttl-days: 7
    chunk-min-size: 5242880  # 5MB
server:
  port: 5000
management:
  endpoints:
    web:
      exposure:
        include: health,metrics,prometheus
  metrics:
    export:
      prometheus:
        enabled: true
logging:
  level:
    com.jreg: INFO
```

### Dockerfile

```dockerfile
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY target/jreg-*.jar app.jar
EXPOSE 5000
ENTRYPOINT ["java", "-jar", "app.jar"]
```

### Docker Compose (Local Development)

```yaml
version: '3.8'
services:
  jreg:
    build: .
    ports:
      - "5000:5000"
    environment:
      S3_BUCKET_NAME: jreg-local
      AWS_REGION: us-east-1
      S3_ENDPOINT: http://localstack:4566
    depends_on:
      - localstack
  
  localstack:
    image: localstack/localstack:latest
    ports:
      - "4566:4566"
    environment:
      SERVICES: s3
```

---

## Summary of Key Decisions

| Area | Decision | Rationale |
|------|----------|-----------|
| Storage | AWS S3 only (no database) | Content-addressable model matches S3, operational simplicity |
| Language | Java 21 + Spring Boot 3.2 | Virtual threads for concurrency, production-ready ecosystem |
| Upload Sessions | S3 metadata + lifecycle cleanup | No extra service, S3 multipart for large blobs |
| Tag Listing | S3 LIST with app-side sorting | Acceptable performance, avoid database complexity |
| Referrers | Index file per digest in S3 | Simple implementation, supports filtering |
| Validation | Regex + SHA-256 on upload | Security and OCI compliance |
| Observability | JSON logs + Prometheus metrics + health | Constitution compliance, production readiness |
| Testing | Unit + Contract + Integration + OCI conformance | TDD compliance, quality gates |

---

## Next Steps (Phase 1)

1. Generate OpenAPI 3.0 specification for all OCI endpoints
2. Create data model definitions (Manifest, Blob, Tag, Digest entities)
3. Write quickstart guide for local development
4. Update agent context with Java/Spring Boot/AWS SDK
