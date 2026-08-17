package com.sosmoch.aws_s3_api.service;


import com.sosmoch.aws_s3_api.dto.FileMetadataResponse;
import com.sosmoch.aws_s3_api.dto.FileUploadResponse;
import com.sosmoch.aws_s3_api.exception.FileNotFoundException;
import com.sosmoch.aws_s3_api.exception.InvalidFileException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class S3Service {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;

    private static final String FOLDER_PREFIX = "uploads/";
    private static final long MAX_FILE_SIZE = 10*1024*1024;
    private static final List<String> ALLOWED_MIME_TYPES = List.of(
            "image/png",
            "image/jpeg",
            "application/pdf"
    );
    private static final List<String> FORBIDDEN_EXTENSIONS = List.of(
            ".exe", ".bat", ".sh", ".cmd", ".msi", ".jar", ".com"
    );

    @Value("${cloud.aws.bucket.name}")
    private String bucketName;

    // UPLOAD
    public FileUploadResponse uploadFile(MultipartFile file) throws IOException {

        log.info("Validating upload file: {}", file.getOriginalFilename());
        validateFile(file);

        String uniqueFileName = FOLDER_PREFIX + UUID.randomUUID() + "_" + file.getOriginalFilename();
        log.info("Uploading file ...");

        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(uniqueFileName)
                .contentType(file.getContentType())
                .contentLength(file.getSize())
                .build();

        s3Client.putObject(putObjectRequest, RequestBody.fromBytes(file.getBytes()));
        log.info("Upload completed");

        String fileUrl = s3Client.utilities().getUrl(GetUrlRequest.builder().bucket(bucketName).key(uniqueFileName).build()).toExternalForm();

        return new FileUploadResponse(uniqueFileName, fileUrl);
    }

    // DOWNLOAD through springboot app
    public byte[] downloadFile(String key){
        key = FOLDER_PREFIX + key;
        log.info("Downloading {}", key);
        try {

            ResponseBytes<GetObjectResponse> objectAsBytes = s3Client.getObjectAsBytes(GetObjectRequest.builder()
                            .bucket(bucketName)
                            .key(key)
                    .build());
            log.info("Download completed successfully for key: {}", key);

            return objectAsBytes.asByteArray();
        } catch (NoSuchKeyException e) {
            log.warn("S3 Object download failed: Key {} not found in bucket {}", key, bucketName);
            throw new FileNotFoundException("File not found: " + key);
        }
    }

    // GENERATE through presigned url
    public String generatePresignedUrl(String key){
        key = FOLDER_PREFIX + key;
        log.info("Generating presigned URL for key: {}", key);
        try {
            HeadObjectRequest headObjectRequest = HeadObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build();
            s3Client.headObject(headObjectRequest);

            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build();

            GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                    .signatureDuration(Duration.ofMinutes(10))
                    .getObjectRequest(getObjectRequest)
                    .build();

            PresignedGetObjectRequest presignedGetObjectRequest = s3Presigner.presignGetObject(presignRequest);
            log.info("Presigned URL generated successfully for key: {}", key);
            return presignedGetObjectRequest.url().toExternalForm();

        } catch (NoSuchKeyException e) {
            log.warn("Failed to presign link: Key {} not found in bucket {}", key, bucketName);
            throw new FileNotFoundException("File not found: " + key);
        }
    }

    // DELETE
    public void deleteFile(String key){
        key = FOLDER_PREFIX + key;
        log.info("Attempting to delete file from S3 with key: {}", key);

        try{
            HeadObjectRequest headObjectRequest = HeadObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build();
            s3Client.headObject(headObjectRequest);

            DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build();

            s3Client.deleteObject(deleteObjectRequest);
            log.info("Successfully deleted file with key: {}", key);

        } catch (NoSuchKeyException e){
            log.warn("Delete aborted: File not found with key: {}", key);
            throw new FileNotFoundException("Delete failed: File not found with key " + key);
        }
    }

    // LIST
    public List<FileMetadataResponse> listFiles(){
        log.info("Fetching list of all objects");

        ListObjectsV2Request listObjectsV2Request = ListObjectsV2Request.builder()
                .bucket(bucketName)
                .prefix(FOLDER_PREFIX)
                .build();

        ListObjectsV2Response listObjectsV2Response = s3Client.listObjectsV2(listObjectsV2Request);

        log.info("Successfully retrieved {} items from S3 bucket", listObjectsV2Response.contents().size());

        return listObjectsV2Response.contents().stream()
                .filter(s3Object -> !s3Object.key().equals(FOLDER_PREFIX))
                .map(s3Object -> {
                    String visualFileName = s3Object.key().substring(FOLDER_PREFIX.length());

                    return new FileMetadataResponse(
                            visualFileName,
                            s3Object.size(),
                            s3Object.lastModified()
                    );
                })
                .collect(Collectors.toList());

    }

    // helper method
    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()){
            log.warn("Validation failed: Received file is null or empty");
            throw new InvalidFileException("Upload failed: File cannot be empty.");
        }

        if(file.getSize() > MAX_FILE_SIZE){
            log.warn("Validation failed: File size {} exceeds limit", file.getSize());
            throw new InvalidFileException("Upload failed: File size exceeds the maximum limit of 10MB.");
        }

        String contentType = file.getContentType();
        if(contentType == null || !ALLOWED_MIME_TYPES.contains(contentType.toLowerCase())){
            log.warn("Validation failed: Rejected Content-Type '{}'", contentType);
            throw new InvalidFileException("Upload failed: Only PNG, JPEG, and PDF files are allowed.");
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename != null) {
            String lowerCaseName = originalFilename.toLowerCase();
            boolean isExecutable = FORBIDDEN_EXTENSIONS.stream().anyMatch(lowerCaseName::endsWith);
            if (isExecutable) {
                log.error("Security Alert: Blocked upload attempt for executable file: '{}'", originalFilename);
                throw new InvalidFileException("Upload failed: Executable files are strictly prohibited.");
            }
        }
    }
}
