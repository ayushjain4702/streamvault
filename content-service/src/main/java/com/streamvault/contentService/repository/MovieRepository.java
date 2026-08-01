package com.streamvault.contentService.repository;

import com.streamvault.contentService.model.Genre;
import com.streamvault.contentService.model.Movie;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;


public interface MovieRepository extends JpaRepository<Movie,String> {

    List<Movie> findByGenre(Genre genre);
    List<Movie> findByTitleContainingIgnoreCase(String title);
}
