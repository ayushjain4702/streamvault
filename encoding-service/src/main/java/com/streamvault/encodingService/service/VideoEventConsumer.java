package com.streamvault.encodingService.service;

import com.streamvault.encodingService.event.VideoUploadedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class VideoEventConsumer {

    private final EncodingService encodingService;

    /**
     * Listens to video.uploaded kafka topic
     * Triggered when video service uploads a raw video to S3
     *
     * FLOW:
     *
     * video service -> S3 Upload -> Kafka(video.uploaded)
     *                             -> This Consume
     *                             ->Encoding Service ->ffmpeg ->S3
     *                             ->kafka (video.encoded)
     */

    @KafkaListener(
            topics = "video.uploaded",
            groupId = "encoding-service-group"
    )
    public void consumeVideoUploadedEvent(VideoUploadedEvent event){
        log.info("Consumed videoUploadedEvent for movie: {} ,file: {} ",event.getMovieId(),event.getOriginalFileName());

        try{
            encodingService.encodeVideo(event);
        }catch(Exception e){
            log.error("Failed to process encoding for movie: {} - {}",event.getMovieId(),e.getMessage());
        }
    }
}
