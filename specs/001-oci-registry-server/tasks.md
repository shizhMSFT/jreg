# Tasks: OCI-Compliant Registry Server (jreg)

**Input**: Design documents from `/specs/001-oci-registry-server/`
**Prerequisites**: plan.md ✅, spec.md ✅, research.md ✅, data-model.md ✅, contracts/ ✅, quickstart.md ✅

**Tests**: Test tasks included per constitution requirement (TDD Non-Negotiable, 80% coverage target)

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3, US4)
- Include exact file paths in descriptions

## Path Conventions

Single Java project structure:
- Main code: `src/main/java/com/jreg/`
- Resources: `src/main/resources/`
- Test code: `src/test/java/com/jreg/`
- Test resources: `src/test/resources/`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Project initialization and basic structure

- [X] T001 Create Maven project structure with `groupId=com.jreg`, `artifactId=jreg`, `version=1.0.0-SNAPSHOT`
- [X] T002 Configure `pom.xml` with Spring Boot 3.2.0, Java 21, AWS SDK for Java 2.x, Jackson, Micrometer, SLF4J/Logback dependencies
- [X] T003 [P] Create `.gitignore` with Java/Maven/IDE patterns
- [X] T004 [P] Create `README.md` with project description and quickstart link
- [X] T005 [P] Create `docker-compose.yml` with LocalStack S3 configuration
- [X] T006 [P] Create `Dockerfile` for containerized deployment
- [X] T007 Create `src/main/java/com/jreg/JregApplication.java` with Spring Boot main class
- [X] T008 Create `src/main/resources/application.yml` with default configuration (server port, logging)
- [X] T009 [P] Create `src/main/resources/application-local.yml` with LocalStack S3 configuration
- [X] T010 [P] Create `src/main/resources/logback-spring.xml` with JSON structured logging configuration

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core infrastructure that MUST be complete before ANY user story can be implemented

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

- [X] T011 Create `src/main/java/com/jreg/config/S3Config.java` - AWS S3 client bean with endpoint, region, credentials configuration
- [X] T012 [P] Create `src/main/java/com/jreg/config/WebConfig.java` - Spring MVC configuration, CORS, content negotiation
- [X] T013 [P] Create `src/main/java/com/jreg/config/MetricsConfig.java` - Micrometer metrics registry, custom meter bindings
- [X] T014 Create `src/main/java/com/jreg/model/Digest.java` - Immutable record for digest (algorithm + hex) with validation
- [X] T015 [P] Create `src/main/java/com/jreg/model/OciError.java` - OCI error response model with code, message, detail fields
- [X] T016 [P] Create `src/main/java/com/jreg/model/ByteRange.java` - Immutable record for byte range (start, end) with parse method
- [X] T017 Create `src/main/java/com/jreg/exception/OciException.java` - Base exception with OCI error code support
- [X] T018 [P] Create `src/main/java/com/jreg/exception/DigestInvalidException.java` - Exception for digest validation failures
- [X] T019 [P] Create `src/main/java/com/jreg/exception/NameInvalidException.java` - Exception for invalid repository/tag names
- [X] T020 Create `src/main/java/com/jreg/exception/GlobalExceptionHandler.java` - @ControllerAdvice mapping exceptions to OCI error responses
- [X] T021 Create `src/main/java/com/jreg/util/DigestCalculator.java` - Streaming SHA-256/SHA-512 digest calculation utility
- [X] T022 [P] Create `src/main/java/com/jreg/util/RegexValidator.java` - Pre-compiled regex patterns for repository names, tags, digests
- [X] T023 [P] Create `src/main/java/com/jreg/util/S3KeyGenerator.java` - Generate S3 keys for blobs, manifests, tags, uploads, referrers
- [X] T024 Create `src/main/java/com/jreg/storage/StorageBackend.java` - Interface defining storage operations (get, put, delete, list)
- [X] T025 Create `src/main/java/com/jreg/storage/S3StorageBackend.java` - S3 implementation with GetObject, PutObject, HeadObject, ListObjectsV2
- [X] T026 Create `src/main/java/com/jreg/controller/VersionController.java` - GET /v2/ endpoint returning API version
- [X] T027 [P] Create `src/main/java/com/jreg/controller/HealthController.java` - Spring Actuator health/readiness endpoints
- [X] T028 Create `src/test/resources/application-test.yml` - Test configuration with LocalStack endpoints
- [X] T029 Write unit test `src/test/java/com/jreg/util/DigestCalculatorTest.java` - Verify SHA-256 calculation correctness
- [X] T030 [P] Write unit test `src/test/java/com/jreg/util/RegexValidatorTest.java` - Test all regex patterns with valid/invalid inputs
- [X] T031 [P] Write unit test `src/test/java/com/jreg/util/S3KeyGeneratorTest.java` - Verify key generation for all entity types
- [X] T032 Write integration test `src/test/java/com/jreg/VersionControllerIntegrationTest.java` - Test GET /v2/ returns 200 OK

**Checkpoint**: Foundation ready - user story implementation can now begin in parallel

---

## Phase 3: User Story 1 - Push Container Images (Priority: P1) 🎯 MVP

**Goal**: Enable Docker/ORAS clients to push container images to jreg, including blobs (layers) and manifests with tag support

**Independent Test**: Build local image with `docker build -t localhost:5000/myrepo/test:v1 .` and push with `docker push localhost:5000/myrepo/test:v1`. Success means all layers upload, manifest stores, and tag points to manifest.

### Tests for User Story 1 - Write FIRST, ensure FAIL before implementation

- [X] T033 [P] [US1] Write contract test `src/test/java/com/jreg/contract/BlobUploadContractTest.java` - POST /v2/{name}/blobs/uploads/ returns 202, PATCH uploads chunk, PUT finalizes with digest match (7/7 tests passing ✅)
- [X] T034 [P] [US1] Write contract test `src/test/java/com/jreg/contract/ManifestPushContractTest.java` - PUT /v2/{name}/manifests/{ref} returns 201, Docker-Content-Digest header present (8/8 tests passing ✅)
- [ ] T035 [P] [US1] Write integration test `src/test/java/com/jreg/integration/DockerPushTest.java` - Use Testcontainers to docker push busybox image, verify blobs/manifests in S3

### Implementation for User Story 1

- [X] T036 [P] [US1] Create `src/main/java/com/jreg/model/Blob.java` - Blob entity with digest, size, mediaType, uploadedAt, s3Key
- [X] T037 [P] [US1] Create `src/main/java/com/jreg/model/Manifest.java` - Manifest entity with digest, repository, mediaType, content, subject, uploadedAt
- [X] T038 [P] [US1] Create `src/main/java/com/jreg/model/Tag.java` - Tag entity with repository, name, manifestDigest, updatedAt
- [X] T039 [P] [US1] Create `src/main/java/com/jreg/model/UploadSession.java` - Upload session with sessionId, repository, uploadedRanges, s3UploadId
- [X] T040 [P] [US1] Create `src/main/java/com/jreg/exception/BlobNotFoundException.java` - Exception for missing blob (BLOB_UNKNOWN)
- [X] T041 [P] [US1] Create `src/main/java/com/jreg/exception/BlobUploadInvalidException.java` - Exception for invalid upload (BLOB_UPLOAD_INVALID)
- [X] T042 [P] [US1] Create `src/main/java/com/jreg/exception/BlobUploadUnknownException.java` - Exception for unknown session (BLOB_UPLOAD_UNKNOWN)
- [X] T043 [P] [US1] Create `src/main/java/com/jreg/exception/ManifestInvalidException.java` - Exception for invalid manifest JSON (MANIFEST_INVALID)
- [X] T044 [P] [US1] Create `src/main/java/com/jreg/exception/ManifestBlobUnknownException.java` - Exception for manifest referencing missing blob (MANIFEST_BLOB_UNKNOWN)
- [X] T045 [US1] Create `src/main/java/com/jreg/service/ValidationService.java` - Validate repository names, tags, digests against OCI regex (uses RegexValidator)
- [X] T046 [US1] Create `src/main/java/com/jreg/service/BlobService.java` - Blob operations: checkExists, getBlob, startUpload, uploadChunk, completeUpload, deleteBlob
- [X] T047 [US1] Create `src/main/java/com/jreg/service/UploadSessionService.java` - Manage upload sessions: create, getStatus, updateRanges, finalize, cleanup (S3 metadata + chunks)
- [X] T048 [US1] Create `src/main/java/com/jreg/service/ManifestService.java` - Manifest operations: getManifest, putManifest (validate JSON, verify blob refs, store with exact bytes), deleteManifest, referrers API
- [X] T049 [US1] Create `src/main/java/com/jreg/service/TagService.java` - Tag operations: createOrUpdateTag, getTagManifestDigest, deleteTag, listTags
- [X] T050 [US1] Create `src/main/java/com/jreg/controller/BlobController.java` - HEAD/GET /v2/{name}/blobs/{digest} (check exists, download blob)
- [X] T051 [US1] Create `src/main/java/com/jreg/controller/UploadController.java` - POST /v2/{name}/blobs/uploads/ (start upload, monolithic, mount), GET/PATCH/PUT/DELETE /v2/{name}/blobs/uploads/{uuid}
- [X] T052 [US1] Create `src/main/java/com/jreg/controller/ManifestController.java` - HEAD/GET/PUT/DELETE /v2/{name}/manifests/{reference} with Content-Type negotiation, referrers API
- [X] T053 [US1] Implement chunked upload support in UploadController - PATCH with Content-Range header, sequential range validation
- [X] T054 [US1] Implement blob deduplication in BlobService - check digest exists before upload, skip if present
- [X] T055 [US1] Implement cross-repository blob mount in UploadController - POST with mount={digest}&from={repo} query params
- [X] T056 [US1] Implement manifest validation in ManifestService - JSON schema validation, mediaType match, blob reference verification, digest validation
- [X] T057 [US1] Add Docker-Content-Digest header to all blob/manifest responses in controllers
- [X] T058 [US1] Add OCI-Subject header support in ManifestController when manifest has subject field
- [X] T059 [US1] Implement S3 multipart upload for large blobs in UploadSessionService (>5MB chunks)
- [X] T060 [US1] Add metrics in BlobService: jreg_blob_uploads_total, jreg_blob_upload_bytes_total, jreg_blob_upload_duration_seconds
- [ ] T061 [US1] Add metrics in ManifestService: jreg_manifest_pushes_total, jreg_manifest_push_duration_seconds
- [ ] T062 [US1] Add structured logging with request_id in MDC filter `src/main/java/com/jreg/config/LoggingFilter.java`
- [ ] T063 [US1] Write unit test `src/test/java/com/jreg/service/BlobServiceTest.java` - Mock S3StorageBackend, verify blob upload flow
- [ ] T064 [P] [US1] Write unit test `src/test/java/com/jreg/service/ManifestServiceTest.java` - Mock storage, verify manifest validation and storage
- [ ] T065 [P] [US1] Write unit test `src/test/java/com/jreg/service/UploadSessionServiceTest.java` - Verify session state management, range validation
- [ ] T066 [P] [US1] Write unit test `src/test/java/com/jreg/service/ValidationServiceTest.java` - Test all OCI regex patterns against spec examples

**Checkpoint**: At this point, User Story 1 should be fully functional - docker push works end-to-end

---

## Phase 4: User Story 2 - Pull Container Images (Priority: P2)

**Goal**: Enable Docker/ORAS clients to pull container images from jreg, supporting multi-arch indexes and HTTP Range requests

**Independent Test**: After pushing test images via US1, run `docker pull localhost:5000/myrepo/test:v1` and verify image downloads successfully and can run as container.

### Tests for User Story 2 - Write FIRST, ensure FAIL before implementation

- [X] T067 [P] [US2] Write contract test `src/test/java/com/jreg/contract/BlobPullContractTest.java` - GET /v2/{name}/blobs/{digest} returns 200 with blob content, HEAD returns 200 with Content-Length (6/6 tests passing ✅)
- [X] T068 [P] [US2] Write contract test `src/test/java/com/jreg/contract/ManifestPullContractTest.java` - GET /v2/{name}/manifests/{ref} returns 200 with exact manifest bytes, supports tag and digest refs (7/7 tests passing ✅)
- [ ] T069 [P] [US2] Write integration test `src/test/java/com/jreg/integration/DockerPullTest.java` - Use Testcontainers to docker pull image pushed in previous test, verify layers download

### Implementation for User Story 2

- [X] T070 [US2] Implement blob download with streaming in BlobController GET /v2/{name}/blobs/{digest} - use S3 GetObject with ResponseInputStream (✅ Already implemented)
- [X] T071 [US2] Implement HTTP Range request support in BlobController - parse Range header, proxy to S3 GetObjectRequest.range(), return 206 Partial Content (✅ Implemented with full RFC 7233 support)
- [X] T072 [US2] Implement manifest pull by tag in ManifestController - lookup tag → digest → manifest, return exact stored bytes (✅ Already implemented)
- [X] T073 [US2] Implement manifest pull by digest in ManifestController - direct S3 lookup, return with correct Content-Type from mediaType (✅ Already implemented)
- [X] T074 [US2] Implement Content-Type negotiation in ManifestController - parse Accept header, match against stored manifest mediaType (✅ Already implemented)
- [ ] T075 [US2] Handle multi-architecture image index pulls - when pulling index by tag, return index JSON, client selects platform-specific manifest
- [X] T076 [US2] Add 404 error handling in BlobController - throw BlobNotFoundException when S3 NoSuchKey, map to BLOB_UNKNOWN error (✅ Already implemented)
- [X] T077 [US2] Add 404 error handling in ManifestController - throw ManifestNotFoundException when tag/digest not found, map to MANIFEST_UNKNOWN error (✅ Already implemented)
- [ ] T078 [US2] Add metrics in BlobService: jreg_blob_downloads_total, jreg_blob_download_bytes_total, jreg_blob_download_duration_seconds
- [ ] T079 [US2] Add metrics in ManifestService: jreg_manifest_pulls_total, jreg_manifest_pull_duration_seconds
- [ ] T080 [P] [US2] Write unit test `src/test/java/com/jreg/controller/BlobControllerTest.java` - Mock BlobService, verify Range header parsing and 206 response
- [ ] T081 [P] [US2] Write unit test `src/test/java/com/jreg/controller/ManifestControllerTest.java` - Mock ManifestService, verify Content-Type negotiation logic

**Checkpoint**: At this point, User Stories 1 AND 2 should both work - full push/pull cycle operational

---

## Phase 5: User Story 3 - Discover Content (Priority: P3)

**Goal**: Enable users to list repository tags and query referrers (artifacts attached to images like signatures/SBOMs)

**Independent Test**: Push multiple tagged images and referrer manifests, then call GET /v2/{name}/tags/list and GET /v2/{name}/referrers/{digest}, verify accurate lists returned.

### Tests for User Story 3 - Write FIRST, ensure FAIL before implementation

- [X] T082 [P] [US3] Write contract test `src/test/java/com/jreg/contract/TagListContractTest.java` - GET /v2/{name}/tags/list returns JSON with tags array, supports n and last pagination params (6/6 tests passing ✅)
- [X] T083 [P] [US3] Write contract test `src/test/java/com/jreg/contract/ReferrersContractTest.java` - GET /v2/{name}/referrers/{digest} returns OCI Image Index with referrer descriptors (5/5 tests passing ✅)
- [ ] T084 [P] [US3] Write integration test `src/test/java/com/jreg/integration/ReferrersTest.java` - Push manifest with subject field, query referrers, verify relationship tracked

### Implementation for User Story 3

- [ ] T085 [P] [US3] Create `src/main/java/com/jreg/model/ReferrersIndex.java` - Referrers index entity with subjectDigest, repository, referrers list
- [ ] T086 [P] [US3] Create `src/main/java/com/jreg/model/Descriptor.java` - OCI descriptor with mediaType, digest, size, annotations, artifactType
- [ ] T087 [US3] Create `src/main/java/com/jreg/service/ReferrersService.java` - Manage referrers index: addReferrer, removeReferrer, listReferrers (S3 JSON index file)
- [X] T088 [US3] Implement tag listing in TagService.listTags - S3 ListObjectsV2 with prefix tags/{repo}/, sort lexically, support pagination ✅
- [X] T089 [US3] Create `src/main/java/com/jreg/controller/TagController.java` - GET /v2/{name}/tags/list with n and last query params, return JSON with Link header for pagination ✅ (implemented in ManifestController)
- [X] T090 [US3] Create `src/main/java/com/jreg/controller/ReferrersController.java` - GET /v2/{name}/referrers/{digest} with artifactType filter support ✅ (implemented in ManifestController)
- [X] T091 [US3] Implement referrer tracking in ManifestService.putManifest - when manifest has subject field, call ReferrersService.addReferrer ✅
- [X] T092 [US3] Implement referrer index file format - OCI Image Index JSON with manifests array, store at referrers/{repo}/{algorithm}/{digest}.json ✅
- [X] T093 [US3] Implement artifactType filtering in ReferrersService - filter referrers list by artifactType query param, add OCI-Filters-Applied header ✅
- [ ] T094 [US3] Implement S3 conditional PUT for referrers index - use If-Match with ETag to prevent lost updates during concurrent pushes
- [X] T095 [US3] Handle empty referrers case - return empty OCI Image Index when no referrers exist (not 404) ✅
- [X] T096 [US3] Add pagination Link header in TagController - compute next URL with last param when more results available ✅
- [ ] T097 [US3] Add metrics in TagService: jreg_tag_list_requests_total, jreg_tag_list_duration_seconds
- [ ] T098 [US3] Add metrics in ReferrersService: jreg_referrers_queries_total, jreg_referrers_query_duration_seconds
- [ ] T099 [P] [US3] Write unit test `src/test/java/com/jreg/service/TagServiceTest.java` - Mock S3 ListObjectsV2, verify lexical sorting and pagination
- [ ] T100 [P] [US3] Write unit test `src/test/java/com/jreg/service/ReferrersServiceTest.java` - Mock S3, verify index file updates with concurrent writes

**Checkpoint**: All core registry features operational - push, pull, discovery all work independently

---

## Phase 6: User Story 4 - Manage Content Lifecycle (Priority: P4)

**Goal**: Enable registry administrators and tools to delete manifests, tags, and blobs for storage management and security compliance

**Independent Test**: Push images, then call DELETE /v2/{name}/manifests/{ref} and DELETE /v2/{name}/blobs/{digest}, verify content removed and GETs return 404.

### Tests for User Story 4 - Write FIRST, ensure FAIL before implementation

- [X] T101 [P] [US4] Write contract test `src/test/java/com/jreg/contract/ManifestDeleteContractTest.java` - DELETE /v2/{name}/manifests/{ref} returns 202, subsequent GET returns 404 (6/6 tests passing ✅)
- [X] T102 [P] [US4] Write contract test `src/test/java/com/jreg/contract/BlobDeleteContractTest.java` - DELETE /v2/{name}/blobs/{digest} returns 202, subsequent HEAD returns 404 (5/5 tests passing ✅)
- [ ] T103 [P] [US4] Write integration test `src/test/java/com/jreg/integration/DeleteWorkflowTest.java` - Push image, delete tag, delete manifest by digest, verify cleanup

### Implementation for User Story 4

- [X] T104 [US4] Implement tag deletion in TagService.deleteTag - S3 DeleteObject for tags/{repo}/{tag}, return 202 Accepted ✅
- [X] T105 [US4] Implement manifest deletion by tag in ManifestController DELETE /v2/{name}/manifests/{tag} - delete tag only, manifest remains if digest-referenced ✅
- [X] T106 [US4] Implement manifest deletion by digest in ManifestController DELETE /v2/{name}/manifests/{digest} - delete manifest S3 object, all tags become dangling ✅
- [X] T107 [US4] Implement blob deletion in BlobService.deleteBlob - S3 DeleteObject for blobs/{algorithm}/{prefix}/{hex}, return 202 Accepted ✅
- [ ] T108 [US4] Implement referrer cleanup on manifest delete - when manifest deleted, update referrers index to remove descriptor
- [ ] T109 [US4] Implement referrer index cleanup when manifest with subject deleted - call ReferrersService.removeReferrer
- [ ] T110 [US4] Add validation in BlobController DELETE - check if blob referenced by manifests (optional safety check), return 405 if referenced
- [X] T111 [US4] Handle 404 cases gracefully - if delete target doesn't exist, still return 202 (idempotent delete) ✅
- [ ] T112 [US4] Add metrics in ManifestService: jreg_manifest_deletes_total, jreg_manifest_delete_duration_seconds
- [ ] T113 [US4] Add metrics in BlobService: jreg_blob_deletes_total, jreg_blob_delete_duration_seconds
- [ ] T114 [P] [US4] Write unit test `src/test/java/com/jreg/service/ManifestServiceTest.java` - Verify tag vs digest delete behavior differs correctly
- [ ] T115 [P] [US4] Write unit test `src/test/java/com/jreg/service/ReferrersServiceTest.java` - Verify referrer removal updates index correctly

**Checkpoint**: All user stories complete - full OCI registry functionality implemented

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Improvements that affect multiple user stories, production readiness

- [ ] T116 [P] Add S3 lifecycle policy configuration in S3Config - 7-day TTL for uploads/{uuid}/ (abandoned session cleanup)
- [ ] T117 [P] Add connection pooling configuration for S3 client in S3Config - max connections, timeout settings for performance
- [X] T118 [P] Add health check in HealthController - verify S3 connectivity, return degraded if S3 unavailable ✅
- [ ] T119 [P] Create `src/main/resources/static/openapi.yaml` - Copy from specs/001-oci-registry-server/contracts/openapi.yaml for Swagger UI
- [ ] T120 [P] Add Swagger UI dependency in pom.xml - Springdoc OpenAPI for interactive API documentation at /swagger-ui.html
- [ ] T121 [P] Add performance logging - log slow operations (>200ms p95 threshold) with details for investigation
- [x] T122 [P] Add request/response logging filter in config/LoggingFilter.java - log method, path, status, duration for all requests ✅ (includes request ID tracking, MDC integration, slow request detection >200ms, client IP extraction, status-based log levels)
- [ ] T123 [P] Optimize tag listing for large repos - implement in-memory caching with short TTL (5s) to reduce S3 ListObjects calls
- [X] T124 [P] Add error details in GlobalExceptionHandler - include repository name, digest in error detail field for better debugging ✅
- [ ] T125 [P] Write integration test `src/test/java/com/jreg/integration/OciConformanceTest.java` - Run OCI Distribution Spec conformance tests against jreg
- [ ] T126 [P] Add README sections - architecture diagram, S3 key structure, performance characteristics, deployment guide
- [ ] T127 [P] Validate quickstart.md - follow all steps, verify Docker push/pull works, ORAS works, metrics accessible
- [ ] T128 [P] Add CONTRIBUTING.md with development workflow - branch strategy, commit conventions, PR template
- [ ] T129 [P] Add security scanning - configure Maven Dependency-Check plugin for CVE scanning in pom.xml
- [ ] T130 [P] Add Checkstyle configuration - define Java code style rules in checkstyle.xml, integrate with Maven build
- [ ] T131 [P] Run full test suite with coverage - verify 80%+ unit test coverage per constitution requirement
- [ ] T132 Final validation - Build project with `mvn clean package`, run via `java -jar target/jreg-*.jar`, execute all quickstart scenarios

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies - can start immediately
- **Foundational (Phase 2)**: Depends on Setup (Phase 1) completion - BLOCKS all user stories
- **User Stories (Phase 3-6)**: All depend on Foundational phase completion
  - User Story 1 (Push) - P1: Can start after Foundational - No dependencies on other stories
  - User Story 2 (Pull) - P2: Can start after Foundational - Independently testable (push test data first)
  - User Story 3 (Discovery) - P3: Can start after Foundational - Independently testable (push test data first)
  - User Story 4 (Lifecycle) - P4: Can start after Foundational - Independently testable (push test data first)
- **Polish (Phase 7)**: Depends on all user stories being complete

### User Story Dependencies

- **User Story 1 (P1 - Push)**: Foundation only - No other story dependencies
- **User Story 2 (P2 - Pull)**: Foundation only - No story dependencies (integration test pushes test data first)
- **User Story 3 (P3 - Discovery)**: Foundation only - No story dependencies (integration test pushes test data first)
- **User Story 4 (P4 - Lifecycle)**: Foundation only - No story dependencies (integration test pushes test data first)

**Key Insight**: All user stories are independently testable! Each integration test pushes its own test data before testing pull/discovery/delete operations.

### Within Each User Story

1. **Write tests FIRST** - All [US#] tests before any [US#] implementation
2. **Verify tests FAIL** - Ensure tests fail before writing implementation (TDD requirement)
3. **Models** - Create domain entities (Blob, Manifest, Tag, UploadSession, etc.)
4. **Services** - Implement business logic (BlobService, ManifestService, etc.)
5. **Controllers** - Implement REST endpoints (BlobController, ManifestController, etc.)
6. **Integration** - Wire together models → services → controllers, verify end-to-end
7. **Verify tests PASS** - Run tests again, ensure they now pass

### Parallel Opportunities

#### Phase 1 (Setup)
All tasks marked [P] can run in parallel:
- T003 (.gitignore), T004 (README), T005 (docker-compose), T006 (Dockerfile), T009 (application-local.yml), T010 (logback.xml)

#### Phase 2 (Foundational)
All tasks marked [P] can run in parallel:
- T012 (WebConfig), T013 (MetricsConfig), T015 (OciError), T016 (ByteRange), T018-T019 (exceptions), T022-T023 (utils), T030-T031 (unit tests)

#### User Story Tests
All tests for a user story marked [P] can run in parallel (after writing them):
- User Story 1: T033, T034, T035 (contract tests, integration test)
- User Story 2: T067, T068, T069 (contract tests, integration test)
- User Story 3: T082, T083, T084 (contract tests, integration test)
- User Story 4: T101, T102, T103 (contract tests, integration test)

#### User Story Models
All models for a user story marked [P] can run in parallel:
- User Story 1: T036-T039 (Blob, Manifest, Tag, UploadSession), T040-T044 (exceptions)
- User Story 3: T085-T086 (ReferrersIndex, Descriptor)

#### User Story Unit Tests
All unit tests marked [P] can run in parallel:
- User Story 1: T064, T065, T066 (service tests)
- User Story 2: T080, T081 (controller tests)
- User Story 3: T099, T100 (service tests)
- User Story 4: T114, T115 (service tests)

#### Phase 7 (Polish)
All tasks marked [P] can run in parallel (16 parallel opportunities):
- T116-T132 (infrastructure, docs, testing, validation)

#### Team Parallelization
With 4 developers after Foundational phase completes:
- Developer A: User Story 1 (T033-T066) - Push functionality
- Developer B: User Story 2 (T067-T081) - Pull functionality
- Developer C: User Story 3 (T082-T100) - Discovery functionality
- Developer D: User Story 4 (T101-T115) - Lifecycle functionality

**Total Parallel Tasks**: 52 tasks marked [P] out of 132 total (39% parallelizable)

---

## Parallel Example: User Story 1 (Push)

```bash
# Step 1: Write all US1 tests in parallel
[P] T033: Contract test BlobUploadContractTest.java
[P] T034: Contract test ManifestPushContractTest.java
[P] T035: Integration test DockerPushTest.java

# Step 2: Run tests, verify they FAIL (no implementation yet)
mvn test -Dtest="**/contract/*,**/integration/DockerPushTest"

# Step 3: Create all US1 models in parallel
[P] T036: Blob.java
[P] T037: Manifest.java
[P] T038: Tag.java
[P] T039: UploadSession.java
[P] T040-T044: Exception classes (5 files)

# Step 4: Create US1 services sequentially (dependencies)
T045: ValidationService.java
T046: BlobService.java (depends on T045)
T047: UploadSessionService.java (depends on T045, T046)
T048: ManifestService.java (depends on T045, T046)
T049: TagService.java (depends on T045, T048)

# Step 5: Create US1 controllers (depends on services)
T050: BlobController.java
T051: UploadController.java
T052: ManifestController.java

# Step 6: Implement features sequentially
T053-T062: Chunked uploads, deduplication, validation, metrics, logging

# Step 7: Write all US1 unit tests in parallel
[P] T063: BlobServiceTest.java
[P] T064: ManifestServiceTest.java
[P] T065: UploadSessionServiceTest.java
[P] T066: ValidationServiceTest.java

# Step 8: Run all tests, verify they PASS
mvn test
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. **Complete Phase 1**: Setup (T001-T010) - ~2 hours
2. **Complete Phase 2**: Foundational (T011-T032) - ~1 day
   - **CHECKPOINT**: Foundation ready - `mvn test` runs, GET /v2/ works
3. **Complete Phase 3**: User Story 1 only (T033-T066) - ~3 days
   - Write tests first (T033-T035), verify FAIL
   - Implement models, services, controllers (T036-T062)
   - Write unit tests (T063-T066), verify coverage >80%
   - **CHECKPOINT**: Run integration test T035 (DockerPushTest), docker push works!
4. **STOP and VALIDATE**: 
   - Build: `mvn clean package`
   - Run: `java -jar target/jreg-*.jar --spring.profiles.active=local`
   - Test: `docker push localhost:5000/myrepo/test:latest`
   - Success: Image layers and manifest stored in S3, metrics visible at /actuator/prometheus
5. **Deploy/Demo MVP**: Single-story registry (push-only) is production-ready

**Estimated Time**: 4-5 days for MVP (push capability only)

### Incremental Delivery

1. **Foundation** (Phase 1-2) → Foundation ready for all stories
2. **+ User Story 1** (Phase 3) → Test independently → **Deploy/Demo MVP** (push works!)
3. **+ User Story 2** (Phase 4) → Test independently → **Deploy/Demo v2** (push + pull complete!)
4. **+ User Story 3** (Phase 5) → Test independently → **Deploy/Demo v3** (+ discovery!)
5. **+ User Story 4** (Phase 6) → Test independently → **Deploy/Demo v4** (full lifecycle!)
6. **+ Polish** (Phase 7) → Production hardening → **Deploy/Demo v1.0** (OCI conformance!)

**Estimated Total Time**: 10-12 days for full implementation

### Parallel Team Strategy

With 4 developers working in parallel after Foundational phase:

**Week 1**:
- All: Complete Setup + Foundational together (Phase 1-2) - 1.5 days
- Once Foundational done (T032 passes):
  - Dev A: User Story 1 - Push (Phase 3, T033-T066) - 3 days
  - Dev B: User Story 2 - Pull (Phase 4, T067-T081) - 2 days
  - Dev C: User Story 3 - Discovery (Phase 5, T082-T100) - 2 days
  - Dev D: User Story 4 - Lifecycle (Phase 6, T101-T115) - 1.5 days

**Week 2**:
- All: Integration testing - verify stories work together - 1 day
- All: Polish & Cross-Cutting (Phase 7, T116-T132) in parallel - 2 days
- All: OCI conformance testing (T125) - 1 day
- All: Final validation and documentation (T127, T132) - 0.5 day

**Estimated Time with 4 Devs**: 6-7 days total

---

## Task Summary

- **Total Tasks**: 132 tasks
- **Parallel Tasks**: 52 tasks marked [P] (39%)
- **User Story Breakdown**:
  - Setup: 10 tasks
  - Foundational: 22 tasks (blocking all stories)
  - User Story 1 (Push - P1): 34 tasks (includes 3 test tasks)
  - User Story 2 (Pull - P2): 15 tasks (includes 3 test tasks)
  - User Story 3 (Discovery - P3): 19 tasks (includes 3 test tasks)
  - User Story 4 (Lifecycle - P4): 15 tasks (includes 3 test tasks)
  - Polish: 17 tasks
- **Test Tasks**: 24 test tasks (contract tests, integration tests, unit tests)
- **MVP Scope**: Phase 1 + 2 + 3 = 66 tasks (User Story 1 only)
- **Full Scope**: All 132 tasks

---

## Notes

- **[P] marker**: Tasks that can run in parallel (different files, no blocking dependencies)
- **[US#] label**: Maps task to specific user story for traceability and independent delivery
- **TDD Required**: Tests must be written FIRST and FAIL before implementation per constitution
- **Independent Stories**: Each user story can be tested independently by pushing test data in integration tests
- **Commit Strategy**: Commit after each task or logical group (model files together, service complete, etc.)
- **Checkpoints**: Stop at phase checkpoints to validate story completeness before proceeding
- **File Conflicts**: Avoid same-file conflicts by completing services before controllers, models before services
- **Constitution Compliance**: 
  - API-First: OpenAPI contract already defined ✅
  - TDD: Test tasks before implementation ✅
  - Observability: Metrics (T060-T061, T078-T079, T097-T098, T112-T113) + JSON logging (T062) ✅
  - Performance: Connection pooling (T117), caching (T123), performance logging (T121) ✅
  - Security: Input validation throughout (T045, T056), CVE scanning (T129) ✅
- **OCI Conformance**: Task T125 runs official OCI conformance tests - must pass 100%
