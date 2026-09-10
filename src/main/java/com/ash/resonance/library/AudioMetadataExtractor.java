package com.ash.resonance.library;

import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.audio.AudioHeader;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;
import org.jaudiotagger.tag.images.Artwork;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Reads whatever tags/artwork are already embedded in an uploaded audio
 * file. Deliberately NOT a Last.fm/MusicBrainz-style enrichment pipeline —
 * this only surfaces what's already in the file, no external calls, no API
 * keys needed. If the person's files already came in well-tagged (a
 * ripper/downloader that embeds metadata), this is most of the value with
 * none of the external-service complexity.
 *
 * NOTE: written against jaudiotagger's API from memory — this repo has no
 * network access to Maven Central to actually compile/run it, so double
 * check method names (getTrackLength, getBitRateAsNumber, getFirstArtwork,
 * etc.) against whatever jaudiotagger version actually resolves, the same
 * way earlier phases flagged unverified API usage before you had a chance
 * to build them.
 */
@Service
public class AudioMetadataExtractor {

    private static final Logger log = LoggerFactory.getLogger(AudioMetadataExtractor.class);

    public Optional<ExtractedMetadata> extract(Path audioFilePath) {
        try {
            AudioFile audioFile = AudioFileIO.read(audioFilePath.toFile());
            AudioHeader header = audioFile.getAudioHeader();
            Tag tag = audioFile.getTag(); // null if the file has no tag at all

            String title = tagField(tag, FieldKey.TITLE);
            String artist = tagField(tag, FieldKey.ARTIST);
            String album = tagField(tag, FieldKey.ALBUM);
            String genre = tagField(tag, FieldKey.GENRE);
            Integer trackNumber = parseLeadingInt(tagField(tag, FieldKey.TRACK));

            long durationMs = header.getTrackLength() * 1000L;
            Integer bitrateKbps = null;
            try {
                bitrateKbps = (int) header.getBitRateAsNumber();
            } catch (Exception ignored) {
                // Some formats/headers don't expose a clean numeric bitrate — not worth failing over.
            }
            String codec = header.getEncodingType();

            byte[] artworkBytes = null;
            String artworkMimeType = null;
            if (tag != null) {
                Artwork artwork = tag.getFirstArtwork();
                if (artwork != null) {
                    artworkBytes = artwork.getBinaryData();
                    artworkMimeType = artwork.getMimeType();
                }
            }

            return Optional.of(new ExtractedMetadata(title, artist, album, genre, trackNumber,
                    durationMs, bitrateKbps, codec, artworkBytes, artworkMimeType));
        } catch (Exception e) {
            // Corrupt/unsupported tags should never fail the upload itself — extraction is
            // a nice-to-have layered on top of an already-successful upload, not a gate on it.
            log.warn("Metadata extraction failed for {}: {}", audioFilePath, e.toString());
            return Optional.empty();
        }
    }

    private String tagField(Tag tag, FieldKey key) {
        if (tag == null) {
            return null;
        }
        String value = tag.getFirst(key);
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    private Integer parseLeadingInt(String value) {
        if (value == null) {
            return null;
        }
        try {
            // Track field is sometimes "3/12" (track 3 of 12) — take the leading number.
            return Integer.parseInt(value.split("/")[0].trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public record ExtractedMetadata(
            String title,
            String artist,
            String album,
            String genre,
            Integer trackNumber,
            long durationMs,
            Integer bitrateKbps,
            String codec,
            byte[] artworkBytes,
            String artworkMimeType
    ) {
        public boolean hasArtwork() {
            return artworkBytes != null && artworkBytes.length > 0;
        }
    }
}
