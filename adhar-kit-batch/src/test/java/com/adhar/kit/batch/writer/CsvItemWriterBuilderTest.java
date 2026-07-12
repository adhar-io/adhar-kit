package com.adhar.kit.batch.writer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.infrastructure.item.file.FlatFileItemWriter;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link CsvItemWriterBuilder}.
 */
class CsvItemWriterBuilderTest {

    @Test
    @DisplayName("csvWriter builds a writer with a name derived from the file path")
    void csvWriterDefault() {
        FlatFileItemWriter<String> writer = CsvItemWriterBuilder
                .<String>csvWriter("/output/orders.csv")
                .delimited(d -> d.names("value"))
                .build();

        assertThat(writer).isNotNull();
        assertThat(writer.getName()).isEqualTo("ordersCsvWriter");
    }

    @Test
    @DisplayName("csvWriter with custom delimiter builds a writer")
    void csvWriterCustomDelimiter() {
        FlatFileItemWriter<String> writer = CsvItemWriterBuilder
                .<String>csvWriter("/output/report.tsv", "\t")
                .delimited(d -> d.delimiter("\t").names("value"))
                .build();

        assertThat(writer).isNotNull();
        assertThat(writer.getName()).isEqualTo("reportCsvWriter");
    }

    @Test
    @DisplayName("csvWriter with header and field names builds a configured writer")
    void csvWriterWithHeader() throws Exception {
        FlatFileItemWriter<String> writer = CsvItemWriterBuilder.csvWriter(
                "/output/orders.csv", "id,customerName", "id", "customerName");

        assertThat(writer).isNotNull();
        assertThat(writer.getName()).isEqualTo("ordersCsvWriter");
    }

    @Test
    @DisplayName("writer name derivation handles paths without an extension")
    void nameWithoutExtension() {
        FlatFileItemWriter<String> writer = CsvItemWriterBuilder
                .<String>csvWriter("/output/datafile")
                .delimited(d -> d.names("value"))
                .build();

        assertThat(writer.getName()).isEqualTo("datafileCsvWriter");
    }
}
