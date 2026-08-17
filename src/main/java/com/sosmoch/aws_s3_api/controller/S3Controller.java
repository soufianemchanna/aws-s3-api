package com.sosmoch.aws_s3_api.controller;


import com.sosmoch.aws_s3_api.dto.FileMetadataResponse;
import com.sosmoch.aws_s3_api.dto.FileUploadResponse;
import com.sosmoch.aws_s3_api.service.S3Service;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/files")
@Tag(name = "S3 File Operations", description = "Endpoints for uploading, downloading, listing, and deleting objects in AWS S3")
public class S3Controller {

    private final S3Service s3Service;

    @PostMapping("/upload")
    @Operation(summary = "Upload a file to S3", description = "Validates constraints (max 10MB, non-empty, PNG/JPEG/PDF only) and structures into specific prefix directory.")
    @ApiResponse(responseCode = "200", description = "File successfully uploaded")
    @ApiResponse(responseCode = "400", description = "Validation failed (empty file, file too large, invalid type, or executable file block)")
    public ResponseEntity<FileUploadResponse> upload(@RequestParam("file") MultipartFile file) throws IOException {
        FileUploadResponse response = s3Service.uploadFile(file);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/download/{key}")
    @Operation(summary = "Download a file from S3", description = "Streams raw binary content back from target bucket via storage keys.")
    @ApiResponse(responseCode = "200", description = "File download stream initiated successfully")
    @ApiResponse(responseCode = "404", description = "Target file key not found in S3 bucket storage")
    public ResponseEntity<byte[]> download(@PathVariable String key){
        byte[] data = s3Service.downloadFile(key);
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=" + key)
                .body(data);
    }

    @DeleteMapping("/{key}")
    @Operation(summary = "Delete an object permanently from S3", description = "Performs S3 Head Object tracking verification prior to dropping references.")
    @ApiResponse(responseCode = "204", description = "Object deleted successfully")
    @ApiResponse(responseCode = "404", description = "Target deletion key does not exist")
    public ResponseEntity<Void> deleteFile(@PathVariable String key){
        s3Service.deleteFile(key);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    @Operation(summary = "List all tracked files", description = "Retrieves structural array listing key names, sizes, and timestamp metadata.")
    @ApiResponse(responseCode = "200", description = "Resource directory array resolved successfully")
    public ResponseEntity<List<FileMetadataResponse>> listFiles(){
        return ResponseEntity.ok(s3Service.listFiles());
    }

    @GetMapping("/view/{key}")
    @Operation(summary = "Get a secure presigned access URL", description = "Generates a temporary AWS download URL valid for 10 minutes.")
    @ApiResponse(responseCode = "200", description = "Presigned URL generated successfully")
    @ApiResponse(responseCode = "404", description = "Target file key not found in S3 bucket storage")
    public ResponseEntity<Map<String, String>> viewFile(@PathVariable String key){
        String url = s3Service.generatePresignedUrl(key);
        return ResponseEntity.ok(Map.of("presignedUrl", url));
    }
}
