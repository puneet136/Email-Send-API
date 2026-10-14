package com.email.bulk.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.LinkOption;

@Service
public class ImportFileStorage {

    private final Path storageDirectory;

    public ImportFileStorage(
            @Value("${app.import.storage-directory}")
            String storageDirectory) {

        this.storageDirectory = Path.of(storageDirectory)
                .toAbsolutePath()
                .normalize();
    }

    public String storeCsv(MultipartFile file) throws IOException {

        Files.createDirectories(storageDirectory);

        Path destination = Files.createTempFile(
                storageDirectory,
                "recipients-",
                ".csv"
        );

        try {
            try (InputStream input = file.getInputStream()) {
                Files.copy(
                        input,
                        destination,
                        StandardCopyOption.REPLACE_EXISTING
                );
            }

            return destination.getFileName().toString();

        } catch (IOException | RuntimeException exception) {

            try {
                Files.deleteIfExists(destination);
            } catch (IOException cleanupException) {
                exception.addSuppressed(cleanupException);
            }

            throw exception;
        }
    }

    public void deleteStoredFile(String storedFilename) throws IOException {

        Path target = storageDirectory
                .resolve(storedFilename)
                .normalize();

        if (!storageDirectory.equals(target.getParent())) {
            throw new IllegalArgumentException(
                    "Invalid stored filename"
            );
        }

        Files.deleteIfExists(target);
    }

    public InputStream openStoredFile(String storedFilename)
            throws IOException {

        Path target = storageDirectory
                .resolve(storedFilename)
                .normalize();

        if (!storageDirectory.equals(target.getParent())) {
            throw new IllegalArgumentException(
                    "Invalid stored filename"
            );
        }

        if (!Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException(
                    "Stored import file is missing or is not a regular file"
            );
        }

        return Files.newInputStream(
                target,
                LinkOption.NOFOLLOW_LINKS
        );
    }
}