package com.ash.resonance.library;

import com.ash.resonance.common.UuidV7;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * A physical blob, identified by its content hash. Deliberately separate
 * from Track: two tracks with different (maybe sloppily-tagged) titles can
 * point at the same MediaFile if their bytes hash identically — that's
 * what makes dedup free. Named MediaFile rather than File to avoid
 * clashing with java.io.File.
 */
@Entity
@Table(name = "files")
public class MediaFile {

	@Id
	private UUID id;

	@Column(name = "content_hash", nullable = false, unique = true)
	private String contentHash;

	@Column(name = "size_bytes", nullable = false)
	private long sizeBytes;

	@Column(name = "mime_type", nullable = false)
	private String mimeType;

	@Column(name = "storage_path", nullable = false)
	private String storagePath;

	@Column(name = "audio_codec")
	private String audioCodec;

	@Column(name = "bitrate_kbps")
	private Integer bitrateKbps;

	@Column(name = "duration_ms")
	private Long durationMs;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt = Instant.now();

	protected MediaFile() {
		// JPA
	}

	public MediaFile(String contentHash, long sizeBytes, String mimeType, String storagePath) {
		this.id = UuidV7.generate();
		this.contentHash = contentHash;
		this.sizeBytes = sizeBytes;
		this.mimeType = mimeType;
		this.storagePath = storagePath;
	}

	public UUID getId() {
		return id;
	}

	public String getContentHash() {
		return contentHash;
	}

	public long getSizeBytes() {
		return sizeBytes;
	}

	public String getMimeType() {
		return mimeType;
	}

	public String getStoragePath() {
		return storagePath;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public String getAudioCodec() {
		return audioCodec;
	}

	public Integer getBitrateKbps() {
		return bitrateKbps;
	}

	public Long getDurationMs() {
		return durationMs;
	}

	public void setAudioProperties(String audioCodec, Integer bitrateKbps, Long durationMs) {
		this.audioCodec = audioCodec;
		this.bitrateKbps = bitrateKbps;
		this.durationMs = durationMs;
	}
}