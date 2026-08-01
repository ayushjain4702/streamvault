package com.streamvault.streamingService.service;

import com.streamvault.streamingService.dto.StreamingResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.webmvc.autoconfigure.error.BasicErrorController;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class StreamingService {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final RedisTemplate<String,String> redisTemplate;
    private final BasicErrorController basicErrorController;

    @Value("${aws.s3.bucket-name}")
    private String bucketName;

    @Value("${aws.s3.presigned-url-expiry}")
    private long presignedUrlExpiry;

    @Value("${streaming.base-url}")
    private String streamingBaseUrl;

    //Redis key for caching streaming URL's
    private final static String STREAMING_URL_CACHE_PREFIX = "streaming:url:";

    /**
     * FLOW:
     * 1. check redis cache for existing presigned URL
     * 2. if cached : return immediately
     * 3. if not cached = generate new presigned URL from S3
     * 4. Cache the URL in Redis
     * 5. Return streaming URL
     *
     * Why presigned URL used ?
     * - S3 bucket is private locker room - videos are not publicly accessible
     * - Presigned url gives temporary access (X minutes)
     * - Prevent unauthorized video downloads
     */

    public StreamingResponse getStreamingUrl(String movieId, String playlistKey){
        log.info("Getting streaming url for movie: {}",movieId);

        String cacheKey = STREAMING_URL_CACHE_PREFIX + movieId;

        //check redis cache first (ignore stale S3 presigned URLs still in cache)
        String cacheUrl = redisTemplate.opsForValue().get(cacheKey);
        if (cacheUrl != null && !cacheUrl.contains("amazonaws.com")) {
            log.info("Returning cached streaming URL for movie: {}", movieId);
            return new StreamingResponse(movieId, cacheUrl,
                    "1080p, 720p, 480p, 360p", presignedUrlExpiry);
        }

        if (cacheUrl != null) {
            log.info("Discarding stale S3 cached URL for movie {}, using proxy URL", movieId);
            redisTemplate.delete(cacheKey);
        }

        // Return proxy URL so browser never fetches S3 directly (avoids S3 CORS)
        String streamingUrl = buildPlaylistProxyUrl(movieId, playlistKey);
        redisTemplate.opsForValue().set(cacheKey, streamingUrl, 55, TimeUnit.MINUTES);

        log.info("Streaming URL generated and cached for movie {}: {}", movieId, streamingUrl);

        return new StreamingResponse(movieId, streamingUrl,
                "1080p, 720p, 480p, 360p", presignedUrlExpiry);
    }

    /**
     * generate a presigned url for S3 object
     * URL expired after configured time
     */
    private String generatePresignedUrl(String key){
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build();

        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(presignedUrlExpiry))
                .getObjectRequest(getObjectRequest)
                .build();

        return s3Presigner.presignGetObject(presignRequest)
                .url().toString();
    }

    /**
     * Invalidated cache streaming url
     * Called when video is re-encoded or updated
     */
    public void invalidateCache(String movieId){
        String cacheKey = STREAMING_URL_CACHE_PREFIX + movieId;
        redisTemplate.delete(cacheKey);

        log.info("Streaming URL cache invalidated for movie {}",movieId);
    }

    /**
     * This is a key method that makes everything secure
     * @param movieId
     * @param playlistPath
     * @return
     */
    public String getSignedPlaylist(String movieId, String playlistPath){
        log.info("Serving playlist for movie {} path {}", movieId, playlistPath);

        //Get base path for this playlist
        String basePath = playlistPath.substring(0,playlistPath.lastIndexOf('/')+1);

        //Read m3u8 content for S3
        String m3u8Content = readFromS3(playlistPath);

        //Rewrite each line that is a segment or playlist reference
        String signedContent = rewriteM3u8SignedUrls(m3u8Content, basePath, movieId);

        return signedContent;
    }

    public byte[] readBytesFromS3(String s3Key) {
        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(s3Key)
                .build();

        try (ResponseInputStream<GetObjectResponse> response = s3Client.getObject(request)) {
            return response.readAllBytes();
        } catch (IOException e) {
            throw new RuntimeException("Failed to read segment from S3: " + s3Key, e);
        }
    }

    private String rewriteM3u8SignedUrls(String m3u8Content, String basePath, String movieId){

        StringBuilder rewritten = new StringBuilder();

        for(String line: m3u8Content.split("\n")){

            String trimmed = line.trim();
            //Skip empty line and comments
            if(trimmed.isEmpty() || trimmed.startsWith("#")){
                rewritten.append(line).append("\n");
                continue;
            }

            //this is a segment or a playlist reference
            //Build a full S3 key and sign it

            String fullKey = basePath + trimmed;

            if (trimmed.endsWith(".ts")) {
                rewritten.append(buildSegmentProxyUrl(movieId, fullKey)).append("\n");
            } else if (trimmed.endsWith(".m3u8")) {
                rewritten.append(buildPlaylistProxyUrl(movieId, fullKey)).append("\n");
            } else {
                rewritten.append(generatePresignedUrl(fullKey)).append("\n");
            }
        }
        return rewritten.toString();
    }

    private String buildPlaylistProxyUrl(String movieId, String s3Key) {
        return streamingBaseUrl + "/api/v1/stream/" + movieId
                + "/playlist?path=" + java.net.URLEncoder.encode(s3Key, java.nio.charset.StandardCharsets.UTF_8);
    }

    private String buildSegmentProxyUrl(String movieId, String s3Key) {
        return streamingBaseUrl + "/api/v1/stream/" + movieId
                + "/segment?path=" + java.net.URLEncoder.encode(s3Key, java.nio.charset.StandardCharsets.UTF_8);
    }

    /**
     * Read file content from S3
     */
    private String readFromS3(String s3Key){
        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(s3Key)
                .build();

        ResponseInputStream<GetObjectResponse> response = s3Client.getObject(request);

        return new BufferedReader(new InputStreamReader(response))
                .lines()
                .collect(Collectors.joining("\n"));
    }


}
