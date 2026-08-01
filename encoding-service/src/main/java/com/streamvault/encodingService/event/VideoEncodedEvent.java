    package com.streamvault.encodingService.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;


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
