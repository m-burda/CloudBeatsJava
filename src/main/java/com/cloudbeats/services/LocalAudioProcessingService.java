package com.cloudbeats.services;

import com.cloudbeats.dto.AudioMetadataExtractionDto;
import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.audio.AudioHeader;
import org.jaudiotagger.audio.exceptions.CannotReadException;
import org.jaudiotagger.audio.exceptions.InvalidAudioFrameException;
import org.jaudiotagger.audio.exceptions.ReadOnlyFileException;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;
import org.jaudiotagger.tag.TagException;
import org.jaudiotagger.tag.images.Artwork;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Service
public class LocalAudioProcessingService implements AudioProcessingService {
    private final FileManagementService fileManagementService;

    @Value("${storage.local.artwork}")
    private Path artworkPath;

    public LocalAudioProcessingService(FileManagementService fileManagementService) {
        this.fileManagementService = fileManagementService;
    }

    public AudioMetadataExtractionDto extractAudioMetadata(String originalFileName, File file) {
        try {
            AudioFile f = AudioFileIO.read(file);
            Tag tag = f.getTag();
            AudioHeader header = f.getAudioHeader();

            if (tag == null) {
                return new AudioMetadataExtractionDto(
                        originalFileName,
                        "Unknown Artist",
                        "Unknown",
                        null,
                        null,
                        null,
                        header != null ? header.getTrackLength() : 0
                );
            }

            String title = tag.getFirst(FieldKey.TITLE);
            String album = tag.getFirst(FieldKey.ALBUM);
            String artistName = tag.getFirst(FieldKey.ARTIST);
            Integer year = null;
            try {
                String yearStr = tag.getFirst(FieldKey.YEAR);
                if (yearStr != null && !yearStr.isEmpty()) {
                    year = Integer.parseInt(yearStr);
                }
            } catch (NumberFormatException e) {
                // Year parsing failed, leave as null
            }

            String albumCoverUrl = null;
            var artwork = tag.getFirstArtwork();
            if (artwork != null) {
                albumCoverUrl = saveArtwork(artwork);
            }

            return new AudioMetadataExtractionDto(
                    title,
                    artistName != null && !artistName.isEmpty() ? artistName : "Unknown Artist",
                    album,
                    albumCoverUrl,
                    null,
                    year,
                    header != null ? header.getTrackLength() : 0
            );
        } catch (CannotReadException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (TagException e) {
            throw new RuntimeException(e);
        } catch (ReadOnlyFileException e) {
            throw new RuntimeException(e);
        } catch (InvalidAudioFrameException e) {
            throw new RuntimeException(e);
        }
    }

    public String saveArtwork(Artwork artwork) {
        var filePath = artworkPath.resolve(generateArtworkName(artwork));
        return fileManagementService.writeData(artwork.getBinaryData(), filePath);
    }

    private String generateArtworkName(Artwork artwork) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hashBytes = md.digest(artwork.getBinaryData());
            String imageHash = HexFormat.of().formatHex(hashBytes).toLowerCase();

            var mimeType = artwork.getMimeType();
            String extension = mimeType.contains("/") ? mimeType.substring(mimeType.lastIndexOf("/") + 1) : "jpg";

            return String.format("%s.%s", imageHash, extension);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

}
