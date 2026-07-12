package com.adhar.kit.batch.reader;

import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.infrastructure.item.database.JpaPagingItemReader;
import org.springframework.batch.infrastructure.item.database.builder.JpaPagingItemReaderBuilder;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link JpaItemReaderBuilder}.
 */
@ExtendWith(MockitoExtension.class)
class JpaItemReaderBuilderTest {

    /** Marker entity type for the reader. */
    public static class Order {
    }

    @Mock
    private EntityManagerFactory emf;

    @Test
    @DisplayName("jpaPagingReader(class, emf) returns a pre-configured builder")
    void preConfiguredBuilder() {
        JpaPagingItemReaderBuilder<Order> builder =
                JpaItemReaderBuilder.jpaPagingReader(Order.class, emf);

        assertThat(builder).isNotNull();

        JpaPagingItemReader<Order> reader = builder
                .queryString("SELECT o FROM Order o")
                .build();
        assertThat(reader.getName()).isEqualTo("OrderReader");
    }

    @Test
    @DisplayName("jpaPagingReader with query and page size builds a reader")
    void buildsWithQueryAndPageSize() throws Exception {
        JpaPagingItemReader<Order> reader = JpaItemReaderBuilder.jpaPagingReader(
                Order.class, emf, "SELECT o FROM Order o", 100);

        assertThat(reader).isNotNull();
        assertThat(reader.getName()).isEqualTo("OrderReader");
    }

    @Test
    @DisplayName("jpaPagingReader with parameter values builds a reader")
    void buildsWithParameterValues() throws Exception {
        JpaPagingItemReader<Order> reader = JpaItemReaderBuilder.jpaPagingReader(
                Order.class, emf,
                "SELECT o FROM Order o WHERE o.status = :status",
                Map.of("status", "PENDING"),
                25);

        assertThat(reader).isNotNull();
        assertThat(reader.getName()).isEqualTo("OrderReader");
    }
}
