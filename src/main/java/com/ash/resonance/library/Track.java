package com.ash.resonance.library;

import com.ash.resonance.common.UuidV7;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tracks", indexes = {
        @Index(name = "idx_tracks_owner_active", columnList = "owner_id, deleted_at"),
        @Index(name = "idx_tracks_owner_file", columnList = "owner_id, file_id")
})
public class Track {

    @Id
    private UUID id;

    @Column(name = "file_id")
    private UUID fileId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Source source = Source.LOCAL_UPLOAD;

    @Column(name = "external_id")
    private String externalId;

    @Column(nullable = false)
    private String title;

    private String artist;
    private String album;

    @Column(name = "track_number")
    private Integer trackNumber;

    private String genre;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "artwork_file_id")
    private UUID artworkFileId;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(nullable = false)
    private int version = 1;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected Track() {
        // JPA
    }

    public Track(UUID fileId, UUID ownerId, String title, String artist, String album) {
        this.id = UuidV7.generate();
        this.fileId = fileId;
        this.ownerId = ownerId;
        this.title = title;
        this.artist = artist;
        this.album = album;
    }

    public enum Source {
        LOCAL_UPLOAD, YOUTUBE
    }

    public UUID getId() {
        return id;
    }

    public UUID getFileId() {
        return fileId;
    }

    public Source getSource() {
        return source;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getArtist() {
        return artist;
    }

    public void setArtist(String artist) {
        this.artist = artist;
    }

    public String getAlbum() {
        return album;
    }

    public void setAlbum(String album) {
        this.album = album;
    }

    public Integer getTrackNumber() {
        return trackNumber;
    }

    public void setTrackNumber(Integer trackNumber) {
        this.trackNumber = trackNumber;
    }

    public String getGenre() {
        return genre;
    }

    public void setGenre(String genre) {
        this.genre = genre;
    }

    public UUID getArtworkFileId() {
        return artworkFileId;
    }

    public void setArtworkFileId(UUID artworkFileId) {
        this.artworkFileId = artworkFileId;
    }

    public Long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(Long durationMs) {
        this.durationMs = durationMs;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public int getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public void softDelete() {
        this.deletedAt = Instant.now();
        touch();
    }

    public void touch() {
        this.updatedAt = Instant.now();
        this.version++;
    }
}