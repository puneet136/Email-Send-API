package com.email.bulk.importing;

import com.email.bulk.exception.InvalidImportFileException;
import com.email.bulk.storage.ImportFileStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RecipientCsvParserTest {

    @TempDir
    Path storageDirectory;

    private RecipientCsvParser parser;

    @BeforeEach
    void setUp() {
        ImportFileStorage storage =
                new ImportFileStorage(storageDirectory.toString());

        parser = new RecipientCsvParser(storage);
    }

    @Test
    void readsQuotedEmailsAndTrimsWhitespace() throws IOException {
        String filename = writeCsv(
                "email\r\n"
                        + "\"  puneet@EXAMPLE.COM  \"\r\n"
                        + "rahul@example.com\r\n"
        );

        List<List<CsvRecipientRow>> batches = read(filename);

        assertEquals(1, batches.size());
        assertEquals(
                List.of(
                        new CsvRecipientRow(
                                2,
                                "puneet@EXAMPLE.COM",
                                null
                        ),
                        new CsvRecipientRow(
                                3,
                                "rahul@example.com",
                                null
                        )
                ),
                batches.get(0)
        );
    }

    @Test
    void acceptsUtf8BomBeforeHeader() throws IOException {
        String filename = writeCsv(
                "\uFEFFemail\r\npuneet@example.com\r\n"
        );

        List<List<CsvRecipientRow>> batches = read(filename);

        assertEquals(
                List.of(
                        List.of(
                                new CsvRecipientRow(
                                        2,
                                        "puneet@example.com",
                                        null
                                )
                        )
                ),
                batches
        );
    }

    @Test
    void rejectsEmptyFile() throws IOException {
        String filename = writeCsv("");
        List<List<CsvRecipientRow>> batches = new ArrayList<>();

        InvalidImportFileException exception = assertThrows(
                InvalidImportFileException.class,
                () -> parser.readBatches(filename, batches::add)
        );

        assertEquals("CSV file is empty", exception.getMessage());
        assertTrue(batches.isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "name",
            "Email",
            "email,name",
            ""
    })
    void rejectsInvalidHeader(String header) throws IOException {
        String filename = writeCsv(
                header + "\r\npuneet@example.com\r\n"
        );

        List<List<CsvRecipientRow>> batches = new ArrayList<>();

        InvalidImportFileException exception = assertThrows(
                InvalidImportFileException.class,
                () -> parser.readBatches(filename, batches::add)
        );

        assertEquals(
                "CSV must contain exactly one column with header: email",
                exception.getMessage()
        );
        assertTrue(batches.isEmpty());
    }

    @Test
    void acceptsHeaderOnlyWithoutEmittingBatch() throws IOException {
        String filename = writeCsv("email\r\n");

        assertTrue(read(filename).isEmpty());
    }

    @Test
    void preservesBlankRowsAsErrorsWithRecordNumbers()
            throws IOException {

        String filename = writeCsv(
                "email\r\n"
                        + "\r\n"
                        + "   \r\n"
                        + "puneet@example.com\r\n"
        );

        List<List<CsvRecipientRow>> batches = read(filename);

        assertEquals(1, batches.size());
        assertEquals(
                List.of(
                        new CsvRecipientRow(
                                2,
                                "",
                                "Email must not be blank"
                        ),
                        new CsvRecipientRow(
                                3,
                                "",
                                "Email must not be blank"
                        ),
                        new CsvRecipientRow(
                                4,
                                "puneet@example.com",
                                null
                        )
                ),
                batches.get(0)
        );
    }

    @Test
    void recordsExtraColumnErrorAndContinuesReading()
            throws IOException {

        String filename = writeCsv(
                "email\r\n"
                        + "puneet@example.com,extra\r\n"
                        + "rahul@example.com\r\n"
        );

        List<List<CsvRecipientRow>> batches = read(filename);

        assertEquals(1, batches.size());
        assertEquals(
                List.of(
                        new CsvRecipientRow(
                                2,
                                null,
                                "Expected exactly one column"
                        ),
                        new CsvRecipientRow(
                                3,
                                "rahul@example.com",
                                null
                        )
                ),
                batches.get(0)
        );
    }

    @Test
    void rejectsUnclosedQuotedRecord() throws IOException {
        String filename = writeCsv(
                "email\r\n\"puneet@example.com"
        );

        List<List<CsvRecipientRow>> batches = new ArrayList<>();

        assertThrows(
                IOException.class,
                () -> parser.readBatches(filename, batches::add)
        );

        assertTrue(batches.isEmpty());
    }

    @Test
    void rejectsMalformedUtf8() throws IOException {
        byte[] header = "email\r\n".getBytes(StandardCharsets.UTF_8);
        byte[] content = new byte[header.length + 2];

        System.arraycopy(
                header,
                0,
                content,
                0,
                header.length
        );

        // Invalid UTF-8 sequence.
        content[header.length] = (byte) 0xC3;
        content[header.length + 1] = (byte) 0x28;

        String filename = "invalid-utf8.csv";
        Files.write(storageDirectory.resolve(filename), content);

        List<List<CsvRecipientRow>> batches = new ArrayList<>();

        assertThrows(
                IOException.class,
                () -> parser.readBatches(filename, batches::add)
        );

        assertTrue(batches.isEmpty());
    }

    @Test
    void splits501RowsIntoIndependentImmutableBatches()
            throws IOException {

        StringBuilder csv = new StringBuilder("email\r\n");

        for (int index = 1; index <= 501; index++) {
            csv.append("user")
                    .append(index)
                    .append("@example.com\r\n");
        }

        String filename = writeCsv(csv.toString());

        List<List<CsvRecipientRow>> batches = read(filename);

        assertEquals(2, batches.size());
        assertEquals(500, batches.get(0).size());
        assertEquals(1, batches.get(1).size());

        List<CsvRecipientRow> allRows = batches.stream()
                .flatMap(List::stream)
                .toList();

        assertEquals(501, allRows.size());

        for (int index = 0; index < allRows.size(); index++) {
            assertEquals(
                    new CsvRecipientRow(
                            index + 2L,
                            "user" + (index + 1) + "@example.com",
                            null
                    ),
                    allRows.get(index)
            );
        }

        assertThrows(
                UnsupportedOperationException.class,
                () -> batches.get(0).clear()
        );

        assertThrows(
                UnsupportedOperationException.class,
                () -> batches.get(1).clear()
        );
    }

    private String writeCsv(String content) throws IOException {
        String filename = "input.csv";

        Files.writeString(
                storageDirectory.resolve(filename),
                content,
                StandardCharsets.UTF_8
        );

        return filename;
    }

    private List<List<CsvRecipientRow>> read(String filename)
            throws IOException {

        List<List<CsvRecipientRow>> batches = new ArrayList<>();

        parser.readBatches(filename, batches::add);

        return batches;
    }
}