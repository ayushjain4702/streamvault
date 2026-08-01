package com.streamvault.contentService.service;

import com.streamvault.contentService.dto.MovieRequest;
import com.streamvault.contentService.dto.MovieResponse;
import com.streamvault.contentService.model.Genre;
import com.streamvault.contentService.model.Movie;
import com.streamvault.contentService.model.VideoStatus;
import com.streamvault.contentService.repository.MovieRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class ContentService {

    private final MovieRepository movieRepository;

    /**
     *
     * Add a new movie to catalog
     * video is not uploaded at this stage
     */
    public MovieResponse addMovie(MovieRequest req){

        log.info("Adding new movie {}",req.getTitle());

        Movie movie = new Movie();
        movie.setTitle(req.getTitle());
        movie.setDescription(req.getDescription());
        movie.setGenre(req.getGenre());
        movie.setDirector(req.getDirector());
        movie.setCast(req.getCast());
        movie.setReleaseYear(req.getReleaseYear());
        movie.setRating(req.getRating());
        movie.setThumbnailUrl(req.getThumbnailUrl());
        movie.setDurationMinutes(req.getDurationMinutes());
        movie.setVideoStatus(VideoStatus.PENDING);

        Movie savedMovie = movieRepository.save(movie);
        log.info("Movie added with id {}",savedMovie.getId());

        return mapToResponse(savedMovie);
    }

    /**
     * Get all movies from catalog
     */
    public List<MovieResponse> getAllMovies(){

        return movieRepository.findAll()
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Get a movie by id
     */
    public MovieResponse getMoviesById(String movieId){

        Movie movie = movieRepository.findById(movieId).
                orElseThrow(() -> new RuntimeException("Movie not found "+movieId));

        return mapToResponse(movie);
    }

    /**
     * Get movies by Genre
     */
    public List<MovieResponse> getMoviesByGenre(Genre genre){

        return movieRepository.findByGenre(genre)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Search movie my title
     */
    public List<MovieResponse> searchMovies(String title){

        return movieRepository.findByTitleContainingIgnoreCase(title)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public void updateVideoKey(String movieId, String videoKey){
        log.info("Updating videokey for movie : {}",movieId);

        Movie movie = movieRepository.findById(movieId)
                .orElseThrow(() -> new RuntimeException("Movie not found: "+movieId));

        movie.setVideoKey(videoKey);
        movie.setVideoStatus(VideoStatus.UPLOADED);

        movieRepository.save(movie);
    }

    public void updateHlsUrl(String movieId,String hlsUrl){
        log.info("Updating HLS URL for movie : {}",movieId);

        Movie movie = movieRepository.findById(movieId)
                .orElseThrow(() -> new RuntimeException("Movie not found: "+movieId));

        movie.setHlsUrl(hlsUrl);
        movie.setVideoStatus(VideoStatus.READY);
        movieRepository.save(movie);

        log.info("Movie {} is ready for streaming",movieId);
    }

    public void updateVideoStatus(String movieId, VideoStatus videoStatus){

        Movie movie = movieRepository.findById(movieId)
                .orElseThrow(() -> new RuntimeException("Movie not found: "+movieId));
        movie.setVideoStatus(videoStatus);
        movieRepository.save(movie);
    }



    private MovieResponse mapToResponse(Movie movie){

        MovieResponse movieResponse = new MovieResponse();
        movieResponse.setId(movie.getId());
        movieResponse.setTitle(movie.getTitle());
        movieResponse.setDescription(movie.getDescription());
        movieResponse.setGenre(movie.getGenre());
        movieResponse.setDirector(movie.getDirector());
        movieResponse.setCast(movie.getCast());
        movieResponse.setReleaseYear(movie.getReleaseYear());
        movieResponse.setRating(movie.getRating());
        movieResponse.setThumbnailUrl(movie.getThumbnailUrl());
        movieResponse.setDurationMinutes(movie.getDurationMinutes());
        movieResponse.setVideoKey(movie.getVideoKey());
        movieResponse.setVideoStatus(movie.getVideoStatus());
        movieResponse.setHlsUrl(movie.getHlsUrl());
        movieResponse.setCreatedAt(movie.getCreatedAt());

        return movieResponse;
    }
}
