package com.streamvault.videoService.controller;

import com.streamvault.videoService.service.VideoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/v1/videos")
@Slf4j
@RequiredArgsConstructor
public class VideoController {

    private final VideoService videoService;

    /**
     * upload video file for a movie
     * Accepts multipart file upload
     */
//    @RequestMapping("/upload/{movieId}")
    @PostMapping(
            value = "/upload/{movieId}",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<String> uploadVideo(@PathVariable String movieId, @RequestParam("file")MultipartFile file) throws IOException {

        log.info("Video upload request for movie {} ,file size {} MB",movieId, file.getSize()/(1024*1024));

        if(file.isEmpty()){
            return ResponseEntity.badRequest().body("File is Empty");
        }
        String videoKey = videoService.uploadVideo(movieId,file);

        return ResponseEntity.ok("Video Uploaded successfully!. Key: "+videoKey+
                "- Encoding started automatically via kafka");
    }
}
