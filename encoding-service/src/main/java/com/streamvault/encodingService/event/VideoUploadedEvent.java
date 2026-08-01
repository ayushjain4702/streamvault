package com.streamvault.encodingService.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Consumed from kafka topic: video.uploaded
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class VideoUploadedEvent {

    private String movieId;
    private String videoKey;
    private String bucketName;
    private String originalFileName;
    private long fileSizeBytes;
}
