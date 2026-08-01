package com.streamvault.streamingService.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class StreamingResponse {

    private String movieId;
    private String streamingUrl;  //presigned HLS master playlist URL
    private String quality;      // available qualities
    private long expiredInMinutes; //URL expiry time
}
