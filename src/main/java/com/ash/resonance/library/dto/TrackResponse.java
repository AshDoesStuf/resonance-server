package com.ash.resonance.library.dto;

import com.ash.resonance.library.Track;

import java.util.UUID;

public record TrackResponse(
		UUID id,
		String title,
		String artist,
		String album,
		Long durationMs,
		String contentHash,
		String deletedAt) {
	public static TrackResponse from(
			Track track,
			String contentHash) {

		return new TrackResponse(
				track.getId(),
				track.getTitle(),
				track.getArtist(),
				track.getAlbum(),
				track.getDurationMs(),
				contentHash,
				track.getDeletedAt() != null
						? track.getDeletedAt().toString()
						: null);
	}
}