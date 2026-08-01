package com.streamvault.contentService.dto;

import com.streamvault.contentService.model.Genre;
import com.streamvault.contentService.model.VideoStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MovieResponse {

    private String id;
    private String title;
    private String description;
    private Genre genre;
    private String director;
    private String cast;
    private int releaseYear;
    private double rating;
    private String thumbnailUrl;
    private int durationMinutes;
    private String videoKey;
    private String hlsUrl;
    private VideoStatus videoStatus;
    private LocalDateTime createdAt;
}
