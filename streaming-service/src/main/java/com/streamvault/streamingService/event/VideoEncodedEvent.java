package com.streamvault.streamingService.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Consumed from Kafka topic: video.encoded
 * Published by Encoding service after ffmpeg processing.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VideoEncodedEvent {
    private String movieId;
    private String hlsUrl; //Master Playlist url for streaming
    private String masterPlaylistKey; //S3 Key of master.m3u8
    private boolean success;
    private String errorMessage; //if encodind failed
}
