package com.ash.resonance.library;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.ash.resonance.library.dto.TrackHashCheckResponse;
import com.ash.resonance.library.dto.TrackHashProjection;

import jakarta.transaction.Transactional;

@Service
@Transactional
public class LibraryService {

  private final TrackRepository trackRepository;

  public LibraryService(TrackRepository trackRepository) {
    this.trackRepository = trackRepository;
  }

  public TrackHashCheckResponse checkHashes(
      UUID ownerId,
      List<String> hashes) {

    if (hashes == null || hashes.isEmpty()) {
      return new TrackHashCheckResponse(List.of());
    }

    List<String> normalized = hashes.stream()
        .filter(Objects::nonNull)
        .map(String::trim)
        .map(String::toLowerCase)
        .filter(hash -> hash.matches("[0-9a-f]{64}"))
        .distinct()
        .toList();

    if (normalized.isEmpty()) {
      return new TrackHashCheckResponse(List.of());
    }

    List<TrackHashProjection> existing = trackRepository.findActiveTrackHashes(
        ownerId,
        normalized);

    Map<String, UUID> existingByHash = new HashMap<>();

    for (TrackHashProjection result : existing) {
      existingByHash.put(
          result.contentHash(),
          result.trackId());
    }

    List<TrackHashCheckResponse.HashResult> results = normalized.stream()
        .map(hash -> {
          UUID trackId = existingByHash.get(hash);

          return new TrackHashCheckResponse.HashResult(
              hash,
              trackId != null,
              trackId);
        })
        .toList();

    return new TrackHashCheckResponse(results);
  }
}
