package com.adhar.kit.batch.reader;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.infrastructure.item.file.FlatFileItemReader;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link CsvItemReaderBuilder}.
 */
class CsvItemReaderBuilderTest {

    /** Simple bean used as the mapping target. */
    public static class Order {
        private long id;
        private String customerName;

        public long getId() {
            return id;
        }

        public void setId(long id) {
            this.id = id;
        }

        public String getCustomerName() {
            return customerName;
        }

        public void setCustomerName(String customerName) {
            this.customerName = customerName;
        }
    }

    @Test
    @DisplayName("csvReader builds a reader with default delimiter")
    void csvReaderDefault() {
        FlatFileItemReader<Order> reader = CsvItemReaderBuilder
                .csvReader("/data/orders.csv", Order.class)
                .delimited(d -> d.names("id", "customerName"))
                .build();

        assertThat(reader).isNotNull();
        assertThat(reader.getName()).isEqualTo("OrderCsvReader");
    }

    @Test
    @DisplayName("csvReader with custom delimiter builds a reader")
    void csvReaderCustomDelimiter() {
        FlatFileItemReader<Order> reader = CsvItemReaderBuilder
                .csvReader("/data/orders.tsv", Order.class, "\t")
                .delimited(d -> d.delimiter("\t").names("id", "customerName"))
                .build();

        assertThat(reader).isNotNull();
        assertThat(reader.getName()).isEqualTo("OrderCsvReader");
    }

    @Test
    @DisplayName("csvReaderWithHeader builds a reader that skips the header line")
    void csvReaderWithHeader() {
        FlatFileItemReader<Order> reader = CsvItemReaderBuilder
                .csvReaderWithHeader("/data/orders.csv", Order.class)
                .delimited(d -> d.names("id", "customerName"))
                .build();

        assertThat(reader).isNotNull();
        assertThat(reader.getName()).isEqualTo("OrderCsvReader");
    }
}
