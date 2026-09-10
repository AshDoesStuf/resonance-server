package com.ash.resonance.library.dto;

import java.util.List;
import java.util.UUID;

public record TrackHashCheckResponse(
    List<HashResult> tracks) {

  public record HashResult(
      String contentHash,
      boolean exists,
      UUID trackId) {
  }
}