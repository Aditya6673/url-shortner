package com.urlshortner.repository;

import com.urlshortner.document.ShortUrl;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ShortUrlRepository extends MongoRepository<ShortUrl, String> {
    Optional<ShortUrl> findByShortCode(String shortCode);
    boolean existsByShortCode(String shortCode);
    List<ShortUrl> findTop10ByOrderByClickCountDesc();
    long countByCreatedAtAfter(LocalDateTime date);
    List<ShortUrl> findAllByOrderByCreatedAtDesc();
}
