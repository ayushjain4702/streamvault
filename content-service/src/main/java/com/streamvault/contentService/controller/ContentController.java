package com.streamvault.contentService.controller;

import com.streamvault.contentService.dto.MovieRequest;
import com.streamvault.contentService.dto.MovieResponse;
import com.streamvault.contentService.model.Genre;
import com.streamvault.contentService.service.ContentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/movies")
@Slf4j
@RequiredArgsConstructor
public class ContentController {

    private final ContentService contentService;

    // Add new movie in catalog
    @PostMapping
    public ResponseEntity<MovieResponse> addMovie(@Valid @RequestBody MovieRequest movieRequest){

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(contentService.addMovie(movieRequest));
    }

    // Get all movie
    @GetMapping
    public ResponseEntity<List<MovieResponse>> getAllMovies(){
        return ResponseEntity.ok(contentService.getAllMovies());
    }

    //Get movies by genre
    @GetMapping("/genre/{genre}")
    public ResponseEntity<List<MovieResponse>> getMoviesByGenre(@PathVariable Genre genre){
        return ResponseEntity.ok(contentService.getMoviesByGenre(genre));
    }

    //Get movies by id
    @GetMapping("/{movieId}")
    public ResponseEntity<MovieResponse> getMoviesById(@PathVariable String movieId){
        return ResponseEntity.ok(contentService.getMoviesById(movieId));
    }

    //Search Movie
    @GetMapping("/search")
    public ResponseEntity<List<MovieResponse>> searchMovies(@RequestParam String title){
        return ResponseEntity.ok(contentService.searchMovies(title));
    }
}
