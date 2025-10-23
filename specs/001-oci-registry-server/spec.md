# Feature Specification: OCI-Compliant Registry Server (jreg)

**Feature Branch**: `001-oci-registry-server`  
**Created**: 2025-10-23  
**Status**: Draft  
**Input**: User description: "Build an OCI-compliant registry server named jreg, strictly implementing all APIs defined by https://github.com/opencontainers/distribution-spec/blob/v1.1.1/spec.md and compatible with https://github.com/opencontainers/image-spec/blob/v1.1.1/spec.md so that docker (and other 3rd party tools like ORAS) and pull and push image to this registry."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Push Container Images (Priority: P1)

Users need to push locally built container images to the jreg registry for storage and distribution. This enables the registry to be populated with content.

**Why this priority**: Before any images can be pulled, they must first be pushed to the registry. Push is the foundational capability that populates the registry with content. Without push, the registry would be empty and pull would have nothing to retrieve.

**Independent Test**: Can be tested by building a local image with `docker build` and executing `docker push localhost:5000/myimage:latest`. Success means all layers and manifests are uploaded and stored in the registry.

**Acceptance Scenarios**:

1. **Given** a user has built a container image locally, **When** they run `docker push localhost:5000/myrepo/newimage:v1.0`, **Then** all image layers (blobs) are uploaded, the manifest is stored, and the tag points to the new manifest
2. **Given** a blob already exists in the registry (by digest), **When** a client attempts to push the same blob, **Then** the registry detects the duplicate and skips the upload (deduplication)
3. **Given** a large blob needs to be uploaded, **When** the client uses chunked upload with multiple PATCH requests, **Then** the registry accepts each chunk and assembles the complete blob
4. **Given** a blob upload session is interrupted, **When** the client resumes by querying the session status, **Then** the registry returns the last uploaded byte position and accepts remaining chunks
5. **Given** a manifest references blobs that exist in the registry, **When** the client uploads the manifest via PUT, **Then** the registry stores the manifest and returns 201 Created
6. **Given** a user pushes a manifest with a `subject` field (referrer), **When** the manifest is uploaded, **Then** the registry indexes this relationship for the referrers API

---

### User Story 2 - Pull Container Images (Priority: P2)

Users need to pull existing container images from the jreg registry using Docker CLI and other OCI-compliant clients. This enables distribution and consumption of stored images.

**Why this priority**: After push capability populates the registry with content, pull enables image distribution and consumption. Together with push, this completes the core registry workflow.

**Independent Test**: Can be fully tested by first pushing test images to the registry, then executing `docker pull localhost:5000/myimage:latest` commands. Success means images are retrieved and can be run as containers.

**Acceptance Scenarios**:

1. **Given** a manifest exists in the registry at `myrepo/myimage:v1.0`, **When** a user runs `docker pull localhost:5000/myrepo/myimage:v1.0`, **Then** Docker successfully downloads all layers and the manifest, and the image becomes available locally
2. **Given** a multi-architecture image index exists in the registry, **When** a user pulls the image by tag, **Then** the appropriate architecture-specific manifest is retrieved based on the client platform
3. **Given** a blob exists in the registry, **When** the client requests the blob by digest via `/v2/<name>/blobs/<digest>`, **Then** the registry returns the blob content with status 200 OK
4. **Given** a manifest does not exist, **When** a user attempts to pull it, **Then** the registry returns 404 Not Found with appropriate error code `MANIFEST_UNKNOWN`
5. **Given** a large blob download is interrupted, **When** the client resumes using HTTP Range requests, **Then** the registry serves the remaining bytes without re-downloading completed portions

---

### User Story 3 - Discover Content (Priority: P3)

Users need to discover what content exists in the registry by listing available tags and querying referrers (artifacts attached to images like signatures or SBOMs).

**Why this priority**: Content discovery is essential for users to understand what's available in the registry and for tooling that manages artifact relationships.

**Independent Test**: Can be tested by pushing multiple tagged images and then calling `/v2/<name>/tags/list` and `/v2/<name>/referrers/<digest>`. Success means accurate lists are returned.

**Acceptance Scenarios**:

1. **Given** a repository contains multiple tags, **When** a user requests `/v2/myrepo/tags/list`, **Then** the registry returns a JSON list of all tags in lexical order
2. **Given** a repository has more tags than the pagination limit, **When** a user requests `/v2/myrepo/tags/list?n=10`, **Then** the registry returns 10 tags and includes a `Link` header pointing to the next page
3. **Given** an image manifest has referrers (e.g., signatures, SBOMs), **When** a user queries `/v2/<name>/referrers/<digest>`, **Then** the registry returns an image index listing all manifests with a `subject` field pointing to that digest
4. **Given** a user wants to filter referrers by type, **When** they request `/v2/<name>/referrers/<digest>?artifactType=application/vnd.example.signature.v1`, **Then** only matching referrers are returned with the `OCI-Filters-Applied` header
5. **Given** the referrers API is not yet enabled, **When** a client receives 404 from `/v2/<name>/referrers/<digest>`, **Then** the client falls back to querying the referrers tag schema

---

### User Story 4 - Manage Content Lifecycle (Priority: P4)

Registry administrators and automated tools need to delete manifests, tags, and blobs to manage storage and remove outdated or vulnerable content.

**Why this priority**: Lifecycle management prevents unbounded storage growth and enables security compliance by removing vulnerable images.

**Independent Test**: Can be tested by pushing images, then calling DELETE endpoints and verifying content is removed and subsequent GETs return 404.

**Acceptance Scenarios**:

1. **Given** a tag exists on a manifest, **When** an administrator deletes the tag via `DELETE /v2/<name>/manifests/<tag>`, **Then** the registry removes the tag and returns 202 Accepted, and subsequent pulls of that tag return 404
2. **Given** a manifest exists with a specific digest, **When** an administrator deletes it via `DELETE /v2/<name>/manifests/<digest>`, **Then** the manifest is removed, all tags pointing to it return 404, and the referrers list is updated
3. **Given** a blob exists and is no longer referenced by any manifest, **When** an administrator deletes it via `DELETE /v2/<name>/blobs/<digest>`, **Then** the blob is removed from storage and returns 404 on subsequent access
4. **Given** content deletion is disabled in the registry configuration, **When** a delete request is made, **Then** the registry returns 405 Method Not Allowed or 400 Bad Request
5. **Given** a manifest has referrers tracked via the referrers tag schema, **When** the manifest is deleted, **Then** clients update the referrers tag to remove the deleted manifest descriptor

---

### Edge Cases

- What happens when a client pushes a manifest that references non-existent blobs (except when the blob has a `subject` field)?
- How does the registry handle concurrent chunked uploads to the same session ID?
- What happens when a client requests a manifest with an `Accept` header for an unsupported media type?
- How does the registry respond to malformed digests or tags that don't match required regex patterns?
- What happens when the registry reaches storage capacity during a blob upload?
- How does the registry handle authentication failures vs. authorization failures?
- What happens when multiple clients simultaneously push different manifests to the same tag?
- How does the registry behave when asked to mount a blob from a repository the client lacks access to?
- What happens if a client tries to push a manifest larger than the registry's size limit (4MB minimum)?
- How does the registry handle requests with both digest and tag as the reference in manifest operations?

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST implement the `/v2/` endpoint returning 200 OK to indicate OCI Distribution Spec compliance
- **FR-002**: System MUST support pulling manifests via `GET /v2/<name>/manifests/<reference>` where reference is either a tag or digest
- **FR-003**: System MUST support pulling blobs via `GET /v2/<name>/blobs/<digest>`
- **FR-004**: System MUST support HEAD requests for both manifests and blobs to check existence without downloading content
- **FR-005**: System MUST support HTTP Range requests for blob downloads to enable resumable pulls
- **FR-006**: System MUST validate repository names against the regex `[a-z0-9]+((\.|_|__|-+)[a-z0-9]+)*(\/[a-z0-9]+((\.|_|__|-+)[a-z0-9]+)*)*`
- **FR-007**: System MUST validate tag names against the regex `[a-zA-Z0-9_][a-zA-Z0-9._-]{0,127}` with a maximum length of 128 characters
- **FR-008**: System MUST support chunked blob uploads using POST (session initiation), PATCH (chunk upload), PUT (finalization) workflow
- **FR-009**: System MUST support monolithic blob uploads via POST then PUT or single POST with digest
- **FR-010**: System MUST support blob deduplication by detecting existing blobs with matching digests
- **FR-011**: System MUST support cross-repository blob mounting via `POST /v2/<name>/blobs/uploads/?mount=<digest>&from=<other_name>`
- **FR-012**: System MUST store manifests in their exact byte representation as provided by clients
- **FR-013**: System MUST support pushing manifests via `PUT /v2/<name>/manifests/<reference>`
- **FR-014**: System MUST validate that manifest `Content-Type` header matches the manifest's `mediaType` field
- **FR-015**: System MUST return `Docker-Content-Digest` header with the canonical digest on successful manifest and blob operations
- **FR-016**: System MUST support the referrers API via `GET /v2/<name>/referrers/<digest>` returning an image index
- **FR-017**: System MUST support filtering referrers by `artifactType` query parameter
- **FR-018**: System MUST return `OCI-Subject` header when processing manifests with a `subject` field
- **FR-019**: System MUST support listing tags via `GET /v2/<name>/tags/list` with pagination support using `n` and `last` parameters
- **FR-020**: System MUST return tags in lexical (case-insensitive alphanumeric) order
- **FR-021**: System MUST support deleting manifests via `DELETE /v2/<name>/manifests/<reference>`
- **FR-022**: System MUST support deleting blobs via `DELETE /v2/<name>/blobs/<digest>`
- **FR-023**: System MUST return appropriate OCI error codes (BLOB_UNKNOWN, MANIFEST_UNKNOWN, NAME_INVALID, DIGEST_INVALID, etc.)
- **FR-024**: System MUST accept manifests with `subject` field even if the referenced manifest doesn't exist yet
- **FR-025**: System MUST support both OCI Image Manifest and OCI Image Index manifest types
- **FR-026**: System MUST handle content negotiation using `Accept` headers for manifest requests
- **FR-027**: System MUST enforce a minimum manifest size limit of 4 megabytes
- **FR-028**: System MUST return 202 Accepted for successful delete operations
- **FR-029**: System MUST return 404 Not Found when requesting non-existent content
- **FR-030**: System MUST return 201 Created when successfully uploading blobs or manifests
- **FR-031**: System MUST persist all uploaded content across server restarts
- **FR-032**: System MUST verify digest parameters match actual content digests during uploads
- **FR-033**: System MUST support querying upload session status via `GET /v2/<name>/blobs/uploads/<reference>`
- **FR-034**: System MUST support `OCI-Chunk-Min-Length` header for specifying minimum chunk sizes
- **FR-035**: System MUST include `Link` headers with `rel="next"` for paginated responses

### Key Entities

- **Repository**: A namespace containing related manifests, blobs, and tags. Identified by name matching the OCI spec regex pattern. Contains zero or more manifests and blobs.
- **Manifest**: A JSON document describing an image or artifact. Contains a `mediaType`, `schemaVersion`, and references to blobs via descriptors. May include a `subject` field linking to another manifest. Stored with exact byte representation.
- **Blob**: Binary content addressed by cryptographic digest. Immutable once stored. May represent image layers, configurations, or arbitrary data. Shared across manifests via digest reference.
- **Tag**: A human-readable name pointing to a manifest. Mutable (can be updated to point to different manifests). Limited to 128 characters. Multiple tags can point to the same manifest.
- **Descriptor**: A reference object containing `mediaType`, `digest`, `size`, and optional `annotations`. Used in manifests to reference blobs and other manifests.
- **Upload Session**: A temporary state tracking chunked blob uploads. Identified by a session ID (UUID). Tracks uploaded byte ranges and enforces sequential chunk uploads.
- **Referrer**: A manifest with a `subject` field that creates a relationship to another manifest. Used for attaching signatures, SBOMs, and other artifacts to images.
- **Digest**: A content identifier in format `<algorithm>:<hex>` (e.g., `sha256:abc123...`). Cryptographically verifies content integrity.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Docker CLI can successfully push and pull multi-layer container images to/from jreg without errors in under 10 seconds for a 100MB image on local network
- **SC-002**: ORAS CLI can successfully push and pull OCI artifacts (non-container content) to/from jreg
- **SC-003**: Registry passes 100% of OCI Distribution Spec conformance tests for Pull workflow category
- **SC-004**: Registry passes 100% of OCI Distribution Spec conformance tests for Push workflow category
- **SC-005**: Registry passes 100% of OCI Distribution Spec conformance tests for Content Discovery workflow category
- **SC-006**: Registry correctly handles interrupted uploads - clients can resume chunked uploads from the last uploaded byte position with 95% success rate
- **SC-007**: Registry correctly deduplicates blobs - pushing the same blob twice results in only one stored copy, saving storage space
- **SC-008**: Registry supports at least 100 concurrent push/pull operations without errors or performance degradation below 1 second per operation
- **SC-009**: Registry API responses comply with OCI spec status codes and headers in 100% of standard scenarios
- **SC-010**: Referrers API returns correct artifact relationships - querying referrers for a manifest returns all manifests with matching `subject` field
- **SC-011**: Tag listing returns results in correct lexical order and pagination works correctly with `Link` headers
- **SC-012**: Deleted content (tags, manifests, blobs) returns 404 Not Found on subsequent access within 1 second of deletion
- **SC-013**: Registry enforces validation rules - malformed repository names, tags, or digests are rejected with appropriate 400 Bad Request responses
- **SC-014**: All stored content persists correctly across registry server restarts - 100% data retention
- **SC-015**: Registry handles storage errors gracefully - when disk is full, it returns appropriate 500 Internal Server Error rather than corrupting data

## Assumptions

- The registry will be deployed in environments where Docker Registry v2 protocol compatibility is expected
- Clients (Docker, containerd, ORAS, etc.) will follow the OCI Distribution Spec client requirements
- The registry will initially support HTTP authentication mechanisms (token-based auth implementation details are out of scope for this spec but endpoints must support authentication)
- The registry will use a content-addressable storage backend where blob filenames are based on their digest
- The registry will run as a single-instance service initially (distributed/clustered operation is out of scope)
- Repository names will typically follow hierarchical patterns (e.g., `org/team/project`) but flat names are also valid
- The registry will implement OCI Distribution Spec v1.1.1 and OCI Image Spec v1.1.1 standards
- Clients will properly validate content digests on their end (registry provides digests but client-side verification is client's responsibility)
- The registry will support standard HTTP/1.1 features including chunked transfer encoding and range requests
- Storage backend can handle concurrent read/write operations safely (file system or object storage with appropriate locking)
