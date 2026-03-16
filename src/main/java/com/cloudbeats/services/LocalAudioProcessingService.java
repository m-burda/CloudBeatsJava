package com.cloudbeats.services;

import com.cloudbeats.db.entities.AudioFileMetadata;
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
import java.util.List;

@Service
public class LocalAudioProcessingService implements AudioProcessingService {
    private final FileManagementService fileManagementService;

    @Value("${cloudbeats.storage.artwork}")
    private Path artworkPath;

    public LocalAudioProcessingService(FileManagementService fileManagementService) {
        this.fileManagementService = fileManagementService;
    }

    public AudioFileMetadata extractAudioMetadata(String originalFileName, File file){
        try {
            AudioFile f = AudioFileIO.read(file);
            Tag tag = f.getTag();
            AudioHeader header = f.getAudioHeader();

            AudioFileMetadata extracted = new AudioFileMetadata();

            if (tag == null) {
                extracted.setTitle(originalFileName);
                extracted.setAlbumArtists(List.of("Unknown Artist"));
                extracted.setAlbum("Unknown");
                return extracted;
            }

            extracted.setTitle(tag.getFirst(FieldKey.TITLE));
            extracted.setAlbum(tag.getFirst(FieldKey.ALBUM));
            extracted.setAlbumArtists(List.of(tag.getFirst(FieldKey.ARTIST)));

            var artwork = tag.getFirstArtwork();
            if (artwork != null) {
                var filePath = artworkPath.resolve(generateArtworkName(artwork));
                fileManagementService.writeData(artwork.getBinaryData(), filePath);
                extracted.setAlbumCoverUrl(filePath.toString());
            }
            extracted.setDuration(header.getTrackLength());
            return extracted;
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
