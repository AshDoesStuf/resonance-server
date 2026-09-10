package com.ash.resonance.upload;

import com.ash.resonance.common.UuidV7;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "uploads")
public class Upload {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "device_id")
    private UUID deviceId;

    @Column(name = "declared_hash", nullable = false)
    private String declaredHash;

    @Column(name = "declared_size", nullable = false)
    private long declaredSize;

    @Column(name = "chunk_size", nullable = false)
    private int chunkSize;

    @Column(name = "received_bytes", nullable = false)
    private long receivedBytes = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.IN_PROGRESS;

    @Column(name = "temp_path")
    private String tempPath;

    @Column(name = "mime_type", nullable = false)
    private String mimeType;

    @Column(nullable = false)
    private String title;

    private String artist;
    private String album;

    @Column(name = "resulting_track_id")
    private UUID resultingTrackId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "completed_at")
    private Instant completedAt;

    protected Upload() {
        // JPA
    }

    public Upload(UUID userId, UUID deviceId, String declaredHash, long declaredSize, int chunkSize,
                  String mimeType, String title, String artist, String album) {
        this.id = UuidV7.generate();
        this.userId = userId;
        this.deviceId = deviceId;
        this.declaredHash = declaredHash;
        this.declaredSize = declaredSize;
        this.chunkSize = chunkSize;
        this.mimeType = mimeType;
        this.title = title;
        this.artist = artist;
        this.album = album;
    }

    public enum Status {
        IN_PROGRESS, COMPLETED, FAILED, ABORTED, DEDUP_HIT
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getDeviceId() {
        return deviceId;
    }

    public String getDeclaredHash() {
        return declaredHash;
    }

    public long getDeclaredSize() {
        return declaredSize;
    }

    public int getChunkSize() {
        return chunkSize;
    }

    public long getReceivedBytes() {
        return receivedBytes;
    }

    public void recordChunk(long offsetEnd) {
        this.receivedBytes = Math.max(this.receivedBytes, offsetEnd);
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public String getTempPath() {
        return tempPath;
    }

    public void setTempPath(String tempPath) {
        this.tempPath = tempPath;
    }

    public String getMimeType() {
        return mimeType;
    }

    public String getTitle() {
        return title;
    }

    public String getArtist() {
        return artist;
    }

    public String getAlbum() {
        return album;
    }

    public UUID getResultingTrackId() {
        return resultingTrackId;
    }

    public void setResultingTrackId(UUID resultingTrackId) {
        this.resultingTrackId = resultingTrackId;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void markCompleted() {
        this.completedAt = Instant.now();
    }
}
