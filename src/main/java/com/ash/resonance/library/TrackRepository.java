package com.ash.resonance.library;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ash.resonance.library.dto.LibraryTrackProjection;
import com.ash.resonance.library.dto.TrackHashProjection;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TrackRepository extends JpaRepository<Track, UUID> {

  /**
   * Cursor page ordered by (updated_at, id) so it's a stable total order —
   * two rows updated in the same instant never get skipped, unlike a
   * timestamp-only cursor. `since`/`sinceId` are the last row's values
   * from the previous page; pass Instant.EPOCH / a nil UUID for the first page.
   */
  @Query("""
      select t from Track t
      where t.ownerId = :ownerId
        and (t.updatedAt > :since or (t.updatedAt = :since and t.id > :sinceId))
      order by t.updatedAt asc, t.id asc
      """)
  List<Track> findPageSinceCursor(@Param("ownerId") UUID ownerId,
      @Param("since") Instant since,
      @Param("sinceId") UUID sinceId,
      Pageable pageable);

  @Query("""
      select new com.ash.resonance.library.dto.TrackHashProjection(
          t.id,
          f.contentHash
      )
      from Track t
      join MediaFile f on f.id = t.fileId
      where t.ownerId = :ownerId
        and t.deletedAt is null
        and f.contentHash in :hashes
      """)
  List<TrackHashProjection> findActiveTrackHashes(
      @Param("ownerId") UUID ownerId,
      @Param("hashes") Collection<String> hashes);

  @Query("""
      select com.ash.resonance.library.dto.LibraryTrackProjection(
          t.id,
          t.title,
          t.artist,
          t.album,
          t.durationMs,
          f.contentHash,
          t.updatedAt,
          t.fileId,
          t.deletedAt
      )
      from Track t
      join MediaFile f on f.id = t.fileId
      where t.ownerId = :ownerId
        and (
            t.updatedAt > :since
            or (t.updatedAt = :since and t.id > :sinceId)
        )
      order by t.updatedAt asc, t.id asc
      """)
  List<LibraryTrackProjection> findLibraryPageSinceCursor(
      @Param("ownerId") UUID ownerId,
      @Param("since") Instant since,
      @Param("sinceId") UUID sinceId,
      Pageable pageable);

  List<Track> findByOwnerIdAndDeletedAtIsNull(UUID ownerId);

  Optional<Track> findFirstByOwnerIdAndFileIdAndDeletedAtIsNull(UUID ownerId, UUID fileId);
}