# AWS S3 REST API Service

A Spring Boot RESTful service designed for handling file storage operations on Amazon S3 buckets. Supports file upload validation, streaming binary downloads, presigned URL generation, file metadata listing, and resource deletion.

---

## Features

- **File Upload**: Handles multipart uploads into specified S3 folder paths.
- **File Download**: Streams raw binary content back to the client.
- **Presigned URLs**: Generates secure temporary download URLs valid for 10 minutes.
- **File Listing**: Lists all objects stored under the tracked directory with metadata (filename, size, last modified).
- **File Deletion**: Validates object existence via `HeadObject` before performing deletion.
- **Security Validation**:
    - Maximum upload size restricted to 10 MB.
    - Allowed MIME types: `image/png`, `image/jpeg`, `application/pdf`.
    - Prohibited file extensions: `.exe`, `.bat`, `.sh`, `.cmd`, `.msi`, `.jar`, `.com`.

---

## Configuration & Profiles

The application uses Spring Profiles (`local` and `dev`) to distinguish between authentication mechanisms and configurations.

### Comparison Table

| Feature / Config | `local` Profile | `dev` Profile |
|---|---|---|
| **AWS Credentials** | `StaticCredentialsProvider` (Access/Secret Key) | `DefaultCredentialsProvider` (IAM Role attached to EC2) |
| **Configuration Source** | `.env` / `application-local.yaml` | `application-dev.yaml` |
| **S3 Client Bean** | Uses explicit keys | Uses EC2 Instance Metadata Service (IMDS) |
| **S3 Presigner Bean** | Uses explicit keys | Uses EC2 Instance Metadata Service (IMDS) |
| **Deployment Target** | Local machine | AWS EC2 Instance |

### Environment Setup

#### `local` Profile Setup
Create a `.env` file in the root directory:
```dotenv
AWS_ACCESS_KEY=your-aws-access-key
AWS_SECRET_KEY=your-aws-secret-key
AWS_BUCKET_NAME=your-bucket-name
ACTIVE_PROFILE=local
```

#### `dev` Profile Setup (AWS EC2)
- Attach an IAM Role with `AmazonS3FullAccess` (or tailored S3 policy) to your EC2 instance.
- Set `ACTIVE_PROFILE=dev` or pass `-Dspring.profiles.active=dev` when starting the JAR. No access keys need to be stored on the EC2 instance.

---

## API Endpoints

Base URL path: `/api/v1/files`

| Method | Endpoint | Description | Status Codes |
|---|---|---|---|
| `POST` | `/upload` | Upload a multipart file (`file`) | `200 OK`, `400 Bad Request` |
| `GET` | `/download/{key}` | Download file stream by object key | `200 OK`, `404 Not Found` |
| `GET` | `/view/{key}` | Get a presigned download URL (valid 10 mins) | `200 OK`, `404 Not Found` |
| `GET` | `/` | List metadata of all stored files | `200 OK` |
| `DELETE` | `/{key}` | Delete an object from S3 | `204 No Content`, `404 Not Found` |

### Endpoint Details

#### 1. Upload File
- **Request**: `POST /api/v1/files/upload` (Form-Data with key `file`)
- **Response**:
```json
{
  "fileName": "uploads/550e8400-e29b-41d4-a716-446655440000_document.pdf",
  "fileUrl": "https://bucket-name.s3.region.amazonaws.com/uploads/..."
}
```

#### 2. Get Presigned URL
- **Request**: `GET /api/v1/files/view/{key}`
- **Response**:
```json
{
  "presignedUrl": "https://bucket-name.s3.region.amazonaws.com/uploads/...?X-Amz-Algorithm=..."
}
```

#### 3. List Files
- **Request**: `GET /api/v1/files`
- **Response**:
```json
[
  {
    "fileName": "550e8400-e29b-41d4-a716-446655440000_document.pdf",
    "sizeInBytes": 1048576,
    "lastModified": "2026-08-21T10:15:30Z"
  }
]
```

---

## Exception Handling

Global exception handling returns formatted JSON responses (`ErrorResponse`):

```json
{
  "message": "Upload failed: File size exceeds the maximum limit of 10MB."
}
```

- **`InvalidFileException` (400 Bad Request)**: Triggered when uploading empty files, unsupported file types, executable extensions, or files exceeding size limits.
- **`FileNotFoundException` (404 Not Found)**: Triggered when requesting, presigning, or deleting a non-existent key in S3.
- **`MaxUploadSizeExceededException` (400 Bad Request)**: Intercepted by Spring's multipart size check threshold.

---

## OpenAPI / Swagger UI

Interactive API documentation and schema testing:

- **Swagger UI**: `http://localhost:8080/swagger-ui.html`
- **OpenAPI Doc**: `http://localhost:8080/v3/api-docs`

---

## Tech Stack

- **Java**: 17
- **Framework**: Spring Boot 4.0.7
- **AWS SDK for Java v2**: `software.amazon.awssdk:s3` (v2.47.5)
- **Documentation**: SpringDoc OpenAPI UI (v3.1.0)
- **Utilities**: Lombok

---

## 🏃 How to Run

### Run Local Profile
```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

### Build & Run Dev Profile (EC2)
```bash
./mvnw clean package -DskipTests
java -jar target/aws-s3-api-0.0.1-SNAPSHOT.jar --spring.profiles.active=dev
```