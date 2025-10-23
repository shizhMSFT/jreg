# Data Model: jreg OCI Registry

**Feature**: OCI-Compliant Registry Server  
**Date**: 2025-10-23  
**Status**: Phase 1 Design

## Overview

This document defines the core domain entities for the jreg OCI registry server. Since we're using S3 as the sole storage backend (no database), these entities represent in-memory domain objects and their S3 storage representations.

---

## 1. Digest

**Purpose**: Represents a content identifier using cryptographic hash.

**Fields**:
- `algorithm` (String): Hash algorithm (e.g., "sha256", "sha512")
- `hex` (String): Hexadecimal representation of hash value

**Validation Rules**:
- Algorithm must be supported (sha256, sha512)
- Hex must be valid hexadecimal (0-9, a-f)
- Format: `<algorithm>:<hex>` (e.g., `sha256:abc123...`)

**Relationships**: Referenced by Manifest, Blob, Tag

**Java Implementation**: Value object (immutable record)

```java
public record Digest(String algorithm, String hex) {
    public Digest {
        if (!algorithm.matches("sha256|sha512")) {
            throw new IllegalArgumentException("Unsupported algorithm");
        }
        if (!hex.matches("[a-f0-9]{64,128}")) {
            throw new IllegalArgumentException("Invalid hex digest");
        }
    }
    
    public String toString() {
        return algorithm + ":" + hex;
    }
    
    public static Digest parse(String digestString) {
        String[] parts = digestString.split(":", 2);
        return new Digest(parts[0], parts[1]);
    }
}
```

**S3 Storage**: Part of object keys, not stored as separate entity

---

## 2. Repository

**Purpose**: Logical namespace containing related manifests, blobs, and tags.

**Fields**:
- `name` (String): Repository name (e.g., "library/ubuntu", "myorg/myapp")
- `createdAt` (Instant): Creation timestamp
- `manifestCount` (long): Number of manifests (derived)
- `blobCount` (long): Number of blobs (derived)

**Validation Rules**:
- Name must match OCI regex: `[a-z0-9]+((\.|_|__|-+)[a-z0-9]+)*(\/[a-z0-9]+((\.|_|__|-+)[a-z0-9]+)*)*`
- Name length typically limited to 255 chars (including registry hostname in clients)
- No uppercase letters allowed

**Relationships**:
- Contains many Manifests (1:N)
- Contains many Tags (1:N)
- References many Blobs (M:N through Manifests)

**S3 Storage**:
- Path: `repositories/<name>/_metadata.json` (optional)
- Content:
```json
{
  "name": "library/ubuntu",
  "created_at": "2025-10-23T10:00:00Z"
}
```

**Notes**: 
- Repository existence is implicit (exists if it has manifests/tags)
- Metadata file is optional optimization for statistics
- Listing repositories requires S3 LIST with prefix `tags/` or `manifests/`

---

## 3. Blob

**Purpose**: Represents binary content (image layers, config files) identified by digest.

**Fields**:
- `digest` (Digest): Content-addressable identifier
- `size` (long): Size in bytes
- `mediaType` (String): MIME type (e.g., "application/octet-stream", "application/vnd.oci.image.layer.v1.tar+gzip")
- `uploadedAt` (Instant): Upload timestamp
- `s3Key` (String): S3 object key for this blob

**Validation Rules**:
- Digest must be valid
- Size must be > 0
- Size must match actual S3 object size

**Relationships**:
- Referenced by Manifest descriptors (M:N)
- Shared across multiple manifests (content deduplication)

**S3 Storage**:
- Path: `blobs/<algorithm>/<first-2-chars>/<hex>`
- Example: `blobs/sha256/ab/abcd1234567890...`
- Object metadata:
  - `Content-Type`: mediaType
  - `Content-Length`: size
  - `x-amz-meta-uploaded-at`: timestamp

**Lifecycle**:
1. Created during blob upload (POST + PUT or POST + PATCH + PUT)
2. Immutable after creation (content-addressable)
3. Deleted when no manifests reference it (garbage collection - future feature)

**Query Operations**:
- Get by digest: S3 GetObject
- Check existence: S3 HeadObject
- Delete: S3 DeleteObject

---

## 4. Manifest

**Purpose**: JSON document describing an image or artifact, referencing blobs via descriptors.

**Fields**:
- `digest` (Digest): Digest of manifest JSON (canonical form)
- `repository` (String): Repository name
- `mediaType` (String): Manifest type (e.g., "application/vnd.oci.image.manifest.v1+json", "application/vnd.oci.image.index.v1+json")
- `schemaVersion` (int): OCI schema version (always 2)
- `content` (byte[]): Raw manifest JSON bytes (exact as uploaded)
- `size` (long): Manifest size in bytes
- `subject` (Digest, optional): Reference to another manifest (for referrers)
- `uploadedAt` (Instant): Upload timestamp

**Validation Rules**:
- Must be valid JSON
- mediaType must match Content-Type header
- schemaVersion must be 2
- Digest must match content hash
- If mediaType is image manifest, must have `config` and `layers` fields
- If mediaType is image index, must have `manifests` array

**Relationships**:
- Belongs to one Repository
- References many Blobs via descriptors
- May reference another Manifest via `subject` field
- Referenced by many Tags (M:N)

**Manifest Types**:

### Image Manifest
```json
{
  "schemaVersion": 2,
  "mediaType": "application/vnd.oci.image.manifest.v1+json",
  "config": {
    "mediaType": "application/vnd.oci.image.config.v1+json",
    "size": 1234,
    "digest": "sha256:abc..."
  },
  "layers": [
    {
      "mediaType": "application/vnd.oci.image.layer.v1.tar+gzip",
      "size": 5678,
      "digest": "sha256:def..."
    }
  ],
  "subject": {
    "mediaType": "application/vnd.oci.image.manifest.v1+json",
    "size": 9012,
    "digest": "sha256:ghi..."
  },
  "annotations": {
    "org.opencontainers.image.created": "2025-10-23T10:00:00Z"
  }
}
```

### Image Index (Multi-arch)
```json
{
  "schemaVersion": 2,
  "mediaType": "application/vnd.oci.image.index.v1+json",
  "manifests": [
    {
      "mediaType": "application/vnd.oci.image.manifest.v1+json",
      "size": 1234,
      "digest": "sha256:jkl...",
      "platform": {
        "architecture": "amd64",
        "os": "linux"
      }
    },
    {
      "mediaType": "application/vnd.oci.image.manifest.v1+json",
      "size": 5678,
      "digest": "sha256:mno...",
      "platform": {
        "architecture": "arm64",
        "os": "linux"
      }
    }
  ]
}
```

**S3 Storage**:
- Path: `manifests/<repository>/<algorithm>/<hex>`
- Example: `manifests/library/ubuntu/sha256/abcd1234...`
- Object metadata:
  - `Content-Type`: mediaType
  - `Content-Length`: size
  - `x-amz-meta-uploaded-at`: timestamp
  - `Docker-Content-Digest`: digest (for compatibility)

**Lifecycle**:
1. Uploaded via PUT /v2/<name>/manifests/<reference>
2. Stored with exact byte representation (no reformatting)
3. Can be tagged (multiple tags can point to same manifest)
4. Can be deleted (removes all tags pointing to it)

---

## 5. Tag

**Purpose**: Human-readable pointer to a manifest.

**Fields**:
- `repository` (String): Repository name
- `name` (String): Tag name (e.g., "latest", "v1.0.0")
- `manifestDigest` (Digest): Digest of the manifest this tag points to
- `updatedAt` (Instant): Last update timestamp

**Validation Rules**:
- Name must match OCI regex: `[a-zA-Z0-9_][a-zA-Z0-9._-]{0,127}`
- Maximum length: 128 characters
- Repository must exist
- Referenced manifest must exist

**Relationships**:
- Belongs to one Repository
- Points to one Manifest
- Mutable (can be updated to point to different manifest)

**S3 Storage**:
- Path: `tags/<repository>/<tag-name>`
- Example: `tags/library/ubuntu/latest`
- Content:
```json
{
  "manifest_digest": "sha256:abcd1234...",
  "updated_at": "2025-10-23T10:00:00Z"
}
```

**Lifecycle**:
1. Created/updated when manifest is pushed with tag
2. Can be explicitly deleted (DELETE /v2/<name>/manifests/<tag>)
3. Not deleted when manifest is deleted (becomes dangling reference)

**Query Operations**:
- Get tag → manifest digest: S3 GetObject
- List tags in repository: S3 ListObjectsV2 with prefix `tags/<repository>/`
- Delete tag: S3 DeleteObject

---

## 6. UploadSession

**Purpose**: Tracks state for chunked blob uploads.

**Fields**:
- `sessionId` (UUID): Unique session identifier
- `repository` (String): Target repository
- `uploadedRanges` (List<ByteRange>): Byte ranges uploaded so far
- `totalSize` (Long, optional): Expected total size (if known)
- `createdAt` (Instant): Session creation time
- `lastActivityAt` (Instant): Last chunk upload time
- `contentType` (String): Blob content type
- `s3UploadId` (String, optional): S3 multipart upload ID (for large blobs)

**Validation Rules**:
- Chunks must be uploaded sequentially (no gaps)
- Next chunk start must equal last chunk end + 1
- Session expires after 7 days

**Relationships**: Creates one Blob on finalization

**S3 Storage**:
- Path: `uploads/<session-id>/metadata.json`
- Content:
```json
{
  "session_id": "uuid-1234-5678",
  "repository": "myorg/myapp",
  "uploaded_ranges": [
    {"start": 0, "end": 1048575},
    {"start": 1048576, "end": 2097151}
  ],
  "created_at": "2025-10-23T10:00:00Z",
  "last_activity_at": "2025-10-23T10:05:00Z"
}
```

- Chunk storage: `uploads/<session-id>/chunks/<start>-<end>`

**Lifecycle**:
1. Created via POST /v2/<name>/blobs/uploads/
2. Updated with each PATCH request (chunk upload)
3. Finalized via PUT /v2/<name>/blobs/uploads/<uuid>?digest=...
4. Cleaned up after finalization or 7-day TTL (S3 lifecycle policy)

**State Transitions**:
```
CREATED → UPLOADING → FINALIZING → COMPLETED
                ↓
            ABANDONED (TTL expired)
```

---

## 7. ReferrersIndex

**Purpose**: Index of manifests that reference another manifest via `subject` field.

**Fields**:
- `subjectDigest` (Digest): Digest of the manifest being referenced
- `repository` (String): Repository name
- `referrers` (List<ReferrerDescriptor>): List of referrer manifests

**ReferrerDescriptor Fields**:
- `mediaType` (String): Referrer manifest media type
- `digest` (Digest): Referrer manifest digest
- `size` (long): Referrer manifest size
- `artifactType` (String): Type of artifact (e.g., "application/vnd.example.signature.v1")
- `annotations` (Map<String, String>): Referrer manifest annotations

**S3 Storage**:
- Path: `referrers/<repository>/<algorithm>/<hex>.json`
- Example: `referrers/library/ubuntu/sha256/abcd1234....json`
- Content: OCI Image Index format
```json
{
  "schemaVersion": 2,
  "mediaType": "application/vnd.oci.image.index.v1+json",
  "manifests": [
    {
      "mediaType": "application/vnd.oci.image.manifest.v1+json",
      "size": 1234,
      "digest": "sha256:signature123...",
      "artifactType": "application/vnd.example.signature.v1",
      "annotations": {
        "org.example.signature.type": "gpg"
      }
    }
  ]
}
```

**Lifecycle**:
1. Created when first manifest with `subject` is pushed
2. Updated when additional referrers are pushed
3. Updated when referrer manifest is deleted
4. May be empty (no referrers) - return empty index

**Concurrent Updates**:
- Use S3 conditional PUT (If-Match with ETag) to prevent lost updates
- Retry with exponential backoff on conflict

---

## 8. OciError

**Purpose**: Represents an OCI-compliant error response.

**Fields**:
- `code` (String): Error code (e.g., "BLOB_UNKNOWN", "MANIFEST_INVALID")
- `message` (String): Human-readable error message
- `detail` (Map<String, Object>, optional): Additional context

**Error Codes** (from OCI spec):
- `BLOB_UNKNOWN` - Blob not found
- `BLOB_UPLOAD_INVALID` - Invalid blob upload
- `BLOB_UPLOAD_UNKNOWN` - Upload session not found
- `DIGEST_INVALID` - Digest mismatch
- `MANIFEST_BLOB_UNKNOWN` - Manifest references unknown blob
- `MANIFEST_INVALID` - Invalid manifest JSON
- `MANIFEST_UNKNOWN` - Manifest not found
- `NAME_INVALID` - Invalid repository/tag name
- `NAME_UNKNOWN` - Repository not found
- `SIZE_INVALID` - Size mismatch
- `UNAUTHORIZED` - Authentication required
- `DENIED` - Access forbidden
- `UNSUPPORTED` - Unsupported operation

**JSON Format**:
```json
{
  "errors": [
    {
      "code": "BLOB_UNKNOWN",
      "message": "Blob not found in repository",
      "detail": {
        "digest": "sha256:abc123...",
        "repository": "myorg/myapp"
      }
    }
  ]
}
```

---

## 9. ByteRange

**Purpose**: Represents a byte range for chunked uploads.

**Fields**:
- `start` (long): Starting byte position (inclusive)
- `end` (long): Ending byte position (inclusive)

**Validation Rules**:
- start >= 0
- end >= start
- Ranges must be contiguous (no gaps)

**Java Implementation**:
```java
public record ByteRange(long start, long end) {
    public ByteRange {
        if (start < 0 || end < start) {
            throw new IllegalArgumentException("Invalid byte range");
        }
    }
    
    public long size() {
        return end - start + 1;
    }
    
    public static ByteRange parse(String rangeHeader) {
        // Parse "bytes=0-1048575" or "0-1048575"
        String range = rangeHeader.replace("bytes=", "");
        String[] parts = range.split("-");
        return new ByteRange(Long.parseLong(parts[0]), Long.parseLong(parts[1]));
    }
    
    public String toContentRange(long totalSize) {
        return String.format("bytes %d-%d/%d", start, end, totalSize);
    }
}
```

---

## Entity Relationships Diagram

```
Repository (1) ──────< (N) Manifest
                              │
                              ├──< (N) Blob (via descriptors)
                              │
                              └──< (N) Tag (points to)
                              
Manifest (1) ───subject──> (1) Manifest
    │
    └── tracked in ──> ReferrersIndex

UploadSession (1) ───finalizes to──> (1) Blob

Digest: Used as identifier for Blob, Manifest
```

---

## S3 Object Key Hierarchy

```
s3://jreg-registry/
├── blobs/
│   └── sha256/
│       ├── ab/
│       │   └── abcd1234...      [Blob content]
│       └── cd/
│           └── cdef5678...      [Blob content]
│
├── manifests/
│   └── <repository>/
│       └── sha256/
│           └── <digest>         [Manifest JSON]
│
├── tags/
│   └── <repository>/
│       ├── latest               [Tag pointer JSON]
│       ├── v1.0.0              [Tag pointer JSON]
│       └── stable              [Tag pointer JSON]
│
├── referrers/
│   └── <repository>/
│       └── sha256/
│           └── <digest>.json   [Referrers index]
│
└── uploads/
    └── <session-uuid>/
        ├── metadata.json        [Session state]
        └── chunks/
            ├── 0-1048575       [Chunk 1]
            └── 1048576-2097151 [Chunk 2]
```

---

## Domain Model Notes

1. **No Database Tables**: All entities are stored in S3 with appropriate key structures
2. **Content Addressability**: Blobs and manifests use digest-based keys for deduplication
3. **Immutability**: Blobs and manifests are immutable after creation
4. **Lifecycle Management**: S3 lifecycle policies handle cleanup of abandoned uploads
5. **Consistency**: S3 provides strong read-after-write consistency (no eventual consistency issues)
6. **Concurrency**: S3 conditional operations (ETags) prevent lost updates for referrers index
7. **Query Patterns**: 
   - Point lookups by digest: O(1) via S3 GetObject
   - List tags: O(n) via S3 ListObjects with prefix
   - List referrers: O(1) via index file lookup

---

## Validation Summary

| Entity | Validation Point | Method |
|--------|------------------|--------|
| Repository name | Controller | Regex pattern match |
| Tag name | Controller | Regex pattern match |
| Digest | Service layer | Format + algorithm check |
| Manifest JSON | Service layer | JSON schema validation |
| Blob content | Service layer | Digest verification |
| Upload chunks | Service layer | Sequential range check |

All validation failures result in 400 Bad Request with appropriate OCI error code.
