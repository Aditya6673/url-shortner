package com.urlshortner.repository;

import com.urlshortner.document.ShortUrl;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ShortUrlRepository extends MongoRepository<ShortUrl, String> {
    Optional<ShortUrl> findByShortCodeAndActiveTrue(String shortCode);

    // Deliberately unfiltered: a deleted code stays reserved forever, otherwise
    // code generation could reissue it and old links in the wild would repoint.
    boolean existsByShortCode(String shortCode);

    List<ShortUrl> findAllByActiveTrueOrderByCreatedAtDesc();
    List<ShortUrl> findTop10ByActiveTrueOrderByClickCountDesc();
    long countByActiveTrue();
    long countByActiveTrueAndCreatedAtAfter(LocalDateTime date);
}
