package com.streamvault.encodingService.service;

import com.streamvault.encodingService.event.VideoEncodedEvent;
import com.streamvault.encodingService.event.VideoUploadedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectAclRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.File;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

@Service
@Slf4j
@RequiredArgsConstructor
public class EncodingService {

    private final S3Client s3Client;
    private final KafkaTemplate<String, VideoEncodedEvent> kafkaTemplate;

    @Value("${aws.s3.bucket-name}")
    private String bucketName;

    @Value("${ffmpeg.path}")
    private String ffmpegPath;

    @Value("${encoding.base-path}")
    private String basePath;

    private final String VIDEO_ENCODED_TOPIC = "video.encoded";

    //Video qualities to encode
    //Format: resolution, bitrate, height

    private static final List<int[]> VIDEO_QUALITIES = Arrays.asList(
            new int[]{1920,5000,1080}, //1080p - 5000k bitrate
            new int[]{1280,2800,720},
            new int[]{854,1200,480},
            new int[]{640,800,360}
    );

    /**
     * Main Encoding pipeline
     *
     * Steps:
     * 1. Download raw video from S3
     * 2. Encode to multiple qualities using FFmpeg
     * 3. Generate HLS playlist (.m3u8) for each quality
     * 4. Create Master playlist
     * 5. Upload all encoded files back to s3
     * 6. Publish VideoEncodedEvent to kafka
     */
    public void encodeVideo(VideoUploadedEvent event){
        log.info("Starting encoding platform for movie {}",event.getMovieId());

        //Create a unique path for a movie
        String jobPath = basePath + "/" + event.getMovieId();

        try{

            //Create a temp dir
            Files.createDirectories(Paths.get(jobPath));
            Files.createDirectories(Paths.get(jobPath + "/encoded"));

            //Step 1: Download raw video from S3
            String localVideoPath = jobPath + "/raw_video.mp4";
            downloadFromS3(event.getVideoKey(),localVideoPath);
            log.info("Raw video downloaded to : {} ",localVideoPath);

            //Step 2,3 : Encode to multiple qualities + generate HLS
            for(int[] qualities: VIDEO_QUALITIES){

                int width = qualities[0];
                int bitrate = qualities[1];
                int height = qualities[2];

                String qualityDir = jobPath + "/encoded/" + height + "p";
                Files.createDirectories(Paths.get(qualityDir));

                encodeToHLS(localVideoPath,qualityDir,width,bitrate,height);
                log.info("Encoded {}p successfully",height);
            }

            //Step4 : Generate Master playlist
            String masterPlaylistPath = jobPath + "/encoded/master.m3u8";
            generateMasterPlaylist(masterPlaylistPath);
            log.info("Master Playlist generated");

            //Step5: Upload all resource file to S3
            String encodedPrefix = "encoded/" + event.getMovieId() + "/";
            uploadEncodedFilesToS3(jobPath+"/encoded",encodedPrefix);
            log.info("All encoded files uploaded to S3");

            //Step 6 : Pulish VideoEncodedEvent to kafka
            String masterPlaylistKey = encodedPrefix + "master.m3u8";
            String hlsUrl = "https://" + bucketName + ".s3.amazonaws.com/" + masterPlaylistKey;

            VideoEncodedEvent encodedEvent = new VideoEncodedEvent(
                    event.getMovieId(),
                    hlsUrl,
                    masterPlaylistKey,
                    true,
                    null
            );

            kafkaTemplate.send(VIDEO_ENCODED_TOPIC,event.getMovieId(),encodedEvent);
            log.info("VideoEncodedEvent publishedfor movie {} ",event.getMovieId());


        }catch(Exception e){
            log.info("Encoding failed for movie {} - {} ",event.getMovieId(),e.getMessage());

            //Publish failure event
            VideoEncodedEvent failureEvent = new VideoEncodedEvent(
                    event.getMovieId(),
                    null,
                    null,
                    false,
                    e.getMessage()
            );

            kafkaTemplate.send(VIDEO_ENCODED_TOPIC,event.getMovieId(),failureEvent);
        }finally {
            //cleanup temp files
            cleanUpTempFiles(jobPath);
        }
    }

    /**
     * Download file from S3 to local path
     */
    private void downloadFromS3(String s3key, String localPath){

        GetObjectRequest getObjectRequest  = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(s3key)
                .build();

        s3Client.getObject(getObjectRequest,Paths.get(localPath));
    }

    /**
     * Encode video to HLS using FFmpeg
     *
     * FFmpeg command created:
     * - Multiple .ts segment files (10 Sec each)
     * - A .m3u8 playlist file for this quality
     *
     * @param inputPath
     * @param outputDir
     * @param width
     * @param bitrate
     * @param height
     * @throws IOException
     * @throws InterruptedException
     */
    private void encodeToHLS(String inputPath, String outputDir,
                                int width, int bitrate, int height) throws IOException, InterruptedException {

        String playlistPath = outputDir + "/playlist.m3u8";
        String segmentPattern = outputDir + "/segment_%03d.ts";

        //FFmpeg Command for HLS encoding
        List<String> command = Arrays.asList(
                ffmpegPath,
                "-i", inputPath,                            // Input file
                "-vf", "scale=" + width + ":" + height,    // Scale to resolution
                "-c:v", "libx264",                         // Video codec
                "-b:v", bitrate + "K",                     // Video bitrate
                "-c:a", "aac",                             // Audio codec
                "-b:a", "128k",                            // Audio bitrate
                "-hls_time", "10",                         // 10 second segments
                "-hls_list_size", "0",                     // Keep all segments
                "-hls_segment_filename", segmentPattern,   // Segment naming
                "-f", "hls",                               // Output format HLS
                playlistPath                               // Output playlist
        );

        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.redirectErrorStream(true);
        processBuilder.inheritIO();
        Process process = processBuilder.start();
        int exitCode = process.waitFor();
        if(exitCode!=0){
            throw new RuntimeException("FFmpeg encoding failed with exit code: "+exitCode);
        }
    }

    /**
     * Generate master HLS playlist that references all quality playlists
     * This is the file the video player downloads
     * @param masterPlaylistPath
     * @throws IOException
     */
    private void generateMasterPlaylist(String masterPlaylistPath) throws IOException{
        StringBuilder master = new StringBuilder();
        master.append("#EXTM3U\n");
        master.append("#EXT-X-VERSION:3\n\n");

        // Add each quality to master playlist
        int[][] qualities = {
                {1920, 5000, 1080},
                {1280, 2800, 720},
                {854, 1200, 480},
                {640, 800, 360}
        };

        for (int[] q : qualities) {
            int width = q[0];
            int bitrate = q[1];
            int height = q[2];

            master.append("#EXT-X-STREAM-INF:BANDWIDTH=")
                    .append(bitrate * 1000)
                    .append(", RESOLUTION=").append(width).append("x").append(height)
                    .append(",CODECS=\"avc1.42e01e,mp4a.40.2\"\n");
            master.append(height).append("p/playlist.m3u8\n\n");
        }

        Files.writeString(Paths.get(masterPlaylistPath), master.toString());
    }

    /**
     * Upload all encoded files from local directory back to S3
     * @param localDir
     * @param s3Prefix
     */
    private void uploadEncodedFilesToS3(String localDir, String s3Prefix) throws IOException{

        File directory = new File(localDir);
        uploadDirectoryToS3(directory,localDir,s3Prefix);
    }

    private void uploadDirectoryToS3(File dir, String baseDir, String s3Prefix) throws  IOException{
        for (File file : dir.listFiles()) {
            if (file.isDirectory()) {
                uploadDirectoryToS3(file, baseDir, s3Prefix);
            } else {

                String relativePath = file.getAbsolutePath()
                        .substring(baseDir.length() + 1)
                        .replace("\\", "/");

                String s3Key = s3Prefix + relativePath;

                String contentType = file.getName().endsWith(".m3u8")
                        ? "application/x-mpegURL"
                        : "video/MP2T";

                PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                        .bucket(bucketName)
                        .key(s3Key)
                        .contentType(contentType)
                        .build();

                s3Client.putObject(
                        putObjectRequest,
                        RequestBody.fromFile(file)
                );

                log.debug("Uploaded: {}", s3Key);
            }
        }
    }

    /**
     * Clean up temp files after encoding
     */
    private void cleanUpTempFiles(String jobPath){
        try {

            Path dirPath = Paths.get(jobPath);

            if (Files.exists(dirPath)) {
                Files.walk(dirPath)
                        .sorted(java.util.Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(File::delete);

                log.info("Temp files cleaned up for job: {}", jobPath);
            }
        } catch (IOException e) {
            log.warn("Failed to cleanup temp files: {}", e.getMessage());
        }
    }
}
