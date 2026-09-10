package com.ash.resonance.library.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record TrackHashCheckRequest(
    @NotEmpty @Size(max = 1000) List<@Size(min = 64, max = 64) String> hashes) {
}
