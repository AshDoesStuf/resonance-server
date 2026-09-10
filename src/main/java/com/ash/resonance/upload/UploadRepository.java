package com.ash.resonance.upload;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UploadRepository extends JpaRepository<Upload, UUID> {
    Optional<Upload> findByIdAndUserId(UUID id, UUID userId);
}
