package com.sosmoch.aws_s3_api.dto;

import java.time.Instant;

public record FileMetadataResponse(
        String fileName,
        long sizeInBytes,
        Instant lastModified
) {
}
