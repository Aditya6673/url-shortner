package com.urlshortner.repository;

import com.urlshortner.document.ShortUrl;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ShortUrlRepository extends MongoRepository<ShortUrl, String> {
    Optional<ShortUrl> findByShortCodeAndActiveTrue(String shortCode);

    // Deliberately unfiltered: a deleted code stays reserved forever, otherwise
    // code generation could reissue it and old links in the wild would repoint.
    boolean existsByShortCode(String shortCode);

    // Owner-scoped only. Do not add unscoped list/count queries here — the
    // dashboard used to be global and that was the leak this slice closed.
    // Callers derive counts and top-N from this list rather than re-querying.
    List<ShortUrl> findAllByOwnerIdAndActiveTrueOrderByCreatedAtDesc(String ownerId);
}
