# Modernization Plan: Migrate jreg from AWS to Azure

**Project**: jreg

---

## Technical Framework

- **Language**: Java 21
- **Framework**: Spring Boot 3.2.0
- **Build Tool**: Maven 3.x
- **Key Dependencies**: AWS SDK for Java 2.x (S3), Spring Boot Actuator, Micrometer, Jackson, Logback

---

## Overview

> This migration moves the jreg OCI-compliant container registry server from AWS S3 storage to Azure Blob Storage and deploys the application to Azure App Service. The application currently stores all registry blobs, manifests, and tags in an AWS S3 bucket via the `S3StorageBackend` implementation. The new architecture will:
>
> - Replace the AWS S3 SDK with the Azure Blob Storage SDK, migrating the `S3StorageBackend` to an Azure Blob Storage–backed implementation
> - Remove all AWS SDK dependencies from `pom.xml` and replace them with the equivalent Azure SDK libraries
> - Deploy the modernized application to Azure App Service for managed, scalable hosting
>
> The migration is performed in phases: first replacing the storage backend, then remediating CVEs in updated dependencies, and finally deploying to Azure App Service.

---

## Migration Impact Summary

```
| Application | Original Service      | New Azure Service         | Authentication    | Comments                              |
|-------------|-----------------------|---------------------------|-------------------|---------------------------------------|
| jreg        | AWS S3 (S3Client)     | Azure Blob Storage        | Managed Identity  | Replace S3StorageBackend              |
| jreg        | —                     | Azure App Service         | Managed Identity  | Target deployment platform            |
```

---

## Migration Tasks

### Task 1 — Migrate AWS S3 to Azure Blob Storage

Replace AWS S3 SDK usage with Azure Blob Storage SDK. Migrate the `S3StorageBackend` class and `S3Config` configuration to use the Azure Blob Storage client. Remove the `software.amazon.awssdk:s3` dependency and add the Azure Blob Storage SDK.

### Task 2 — Security & CVE Remediation

Scan all project dependencies for known CVEs and remediate any identified vulnerabilities before deployment.

### Task 3 — Deploy to Azure App Service

Containerize and deploy the jreg application to Azure App Service using the existing Dockerfile (`eclipse-temurin:21-jre-alpine` base image).

---

## Open Questions & Questionnaire

- [x] Q: Should the plan include environment/infrastructure provisioning? → A: No — focus on code migration and deployment using new Azure resources.
- [x] Q: Should the plan include integration testing? → A: No — integration testing was not explicitly requested; skipped.
- [x] Q: Should the plan include security/CVE remediation? → A: Yes — default security task included.
- [x] Q: Which Azure deployment target? → A: Azure App Service, as explicitly requested by the user.
- [x] Q: Does the JDK need upgrading? → A: No — `pom.xml` already specifies `<java.version>21</java.version>` with `eclipse-temurin:21-jre-alpine` in the Dockerfile. The project is already on OpenJDK 21; no upgrade task is required.
- [x] Q: Is AWS SQS used and does it need migrating? → A: No — `pom.xml` contains no SQS dependency and no SQS usage was found in the source code. No SQS migration task is needed.
