package com.email.bulk.importing;

import com.email.bulk.exception.InvalidImportFileException;
import com.email.bulk.storage.ImportFileStorage;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PushbackReader;
import java.io.UncheckedIOException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;

@Component
public class RecipientCsvParser {

    private static final int BATCH_SIZE = 500;

    private final ImportFileStorage fileStorage;

    public RecipientCsvParser(ImportFileStorage fileStorage) {
        this.fileStorage = fileStorage;
    }

    public void readBatches(
            String storedFilename,
            Consumer<List<CsvRecipientRow>> batchConsumer)
            throws IOException {

        CSVFormat format = CSVFormat.RFC4180.builder()
                .setIgnoreEmptyLines(false)
                .get();

        try (
                InputStream input =
                        fileStorage.openStoredFile(storedFilename);

                PushbackReader reader = new PushbackReader(
                        new BufferedReader(
                                new InputStreamReader(
                                        input,
                                        StandardCharsets.UTF_8
                                                .newDecoder()
                                                .onMalformedInput(
                                                        CodingErrorAction.REPORT
                                                )
                                                .onUnmappableCharacter(
                                                        CodingErrorAction.REPORT
                                                )
                                )
                        ),
                        1
                )
        ) {
            skipBom(reader);

            try (CSVParser parser = format.parse(reader)) {

                Iterator<CSVRecord> records = parser.iterator();

                validateHeader(records);

                List<CsvRecipientRow> batch =
                        new ArrayList<>(BATCH_SIZE);

                while (records.hasNext()) {

                    CSVRecord record = records.next();
                    batch.add(toRow(record));

                    if (batch.size() == BATCH_SIZE) {
                        batchConsumer.accept(List.copyOf(batch));
                        batch.clear();
                    }
                }

                if (!batch.isEmpty()) {
                    batchConsumer.accept(List.copyOf(batch));
                }
            }

        } catch (UncheckedIOException exception) {
            throw exception.getCause();
        }
    }

    private void skipBom(PushbackReader reader) throws IOException {

        int firstCharacter = reader.read();

        if (firstCharacter != -1 && firstCharacter != '\uFEFF') {
            reader.unread(firstCharacter);
        }
    }

    private void validateHeader(Iterator<CSVRecord> records) {

        if (!records.hasNext()) {
            throw new InvalidImportFileException(
                    "CSV file is empty"
            );
        }

        CSVRecord header = records.next();

        if (header.size() != 1
                || !"email".equals(header.get(0).strip())) {

            throw new InvalidImportFileException(
                    "CSV must contain exactly one column with header: email"
            );
        }
    }

    private CsvRecipientRow toRow(CSVRecord record) {

        if (record.size() != 1) {
            return new CsvRecipientRow(
                    record.getRecordNumber(),
                    null,
                    "Expected exactly one column"
            );
        }

        String email = record.get(0).strip();

        if (email.isBlank()) {
            return new CsvRecipientRow(
                    record.getRecordNumber(),
                    email,
                    "Email must not be blank"
            );
        }

        return new CsvRecipientRow(
                record.getRecordNumber(),
                email,
                null
        );
    }
}