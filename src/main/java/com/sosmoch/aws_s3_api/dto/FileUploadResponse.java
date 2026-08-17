package com.sosmoch.aws_s3_api.dto;

public record FileUploadResponse(
        String fileName,
        String fileUrl
) {
}
