package com.streamvault.videoService.service;

import com.streamvault.videoService.event.VideoUploadedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class VideoService {

    private final KafkaTemplate<String, VideoUploadedEvent> kafkaTemplate;
    private final S3Client s3Client;

    @Value("${aws.s3.bucket-name}")
    private String bucketName;

    private static final String VIDEO_UPLOADED_TOPIC = "video.uploaded";

    /**
     * Upload video to AWS S3 and publish VideoUploadEvent to kafka
     *
     * FLOW:
     * 1. Receive Multipart video file
     * 2. Generate unique S3 file
     * 3. upload to S3
     * 4. Publish VideoUploadEvent to kafka
     * 5. Encoding Service picks up and start Ffmpeg
     */

    public String uploadVideo(String movieId, MultipartFile file) throws IOException {
        log.info("Starting video upload for movie {},file {} ",movieId, file.getOriginalFilename());

        //Generate unique S3 key for raw video
        //Format: raw/movieId/uuid_filename

        String videoKey = "raw/" + movieId + "/" + UUID.randomUUID() + "_" + file.getOriginalFilename();

        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(videoKey)
                .contentType(file.getContentType())
                .contentLength(file.getSize())
                .build();

        s3Client.putObject(putObjectRequest, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));

        log.info("Video Uploaded to S3 successfully. Key: {}",videoKey);

        //Publish event to Kafka
        //Encoding servive will consume this and start FFmpeg process

        VideoUploadedEvent event = new VideoUploadedEvent(
                movieId,
                videoKey,
                bucketName,
                file.getOriginalFilename(),
                file.getSize()
        );

        kafkaTemplate.send(VIDEO_UPLOADED_TOPIC,event);
        log.info("VideoUplaodedEvent published for movie {}",movieId);

        return videoKey;

    }
}
