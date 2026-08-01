package com.streamvault.contentService.model;

/**
 * Tracks the video processing lifecycle
 *
 * FLOW:
 * PENDING ->UPLOADED ->ENCODING ->ENCODED ->READY
 *                                         ->  FAILED
 */
public enum VideoStatus {
    PENDING, //movie added but not uploaded yet
    UPLOADED, // raw video uplaoded to S3
    ENCODING, //FFmpeg is encoding the video
    ENCODED, // encoding completed
    READY, // HLS playlist ready - can be streamed
    FAILED // Encoding failed
}
