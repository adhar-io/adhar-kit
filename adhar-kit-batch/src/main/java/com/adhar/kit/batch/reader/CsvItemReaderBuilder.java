package com.adhar.kit.batch.reader;

import org.springframework.batch.infrastructure.item.file.FlatFileItemReader;
import org.springframework.batch.infrastructure.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.batch.infrastructure.item.file.mapping.BeanWrapperFieldSetMapper;
import org.springframework.core.io.FileSystemResource;

/**
 * Utility for creating CSV file {@link FlatFileItemReader} instances
 * with common defaults for delimited file reading.
 *
 * <p><b>Usage Example:</b></p>
 * <pre>{@code
 * var reader = CsvItemReaderBuilder.csvReader("/data/orders.csv", Order.class)
 *         .names("id", "customerName", "amount", "status")
 *         .build();
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
public final class CsvItemReaderBuilder {

    private static final String DEFAULT_DELIMITER = ",";

    private CsvItemReaderBuilder() {
        // utility class
    }

    /**
     * Creates a pre-configured {@link FlatFileItemReaderBuilder} for reading CSV files.
     *
     * <p>The builder is initialized with:</p>
     * <ul>
     *   <li>The file resource from the given path</li>
     *   <li>A comma delimiter</li>
     *   <li>A {@link BeanWrapperFieldSetMapper} for the target type</li>
     *   <li>A reader name derived from the target type</li>
     * </ul>
     *
     * @param filePath   the path to the CSV file
     * @param targetType the bean class to map each row to
     * @param <T>        the target bean type
     * @return a pre-configured builder ready for further customization
     */
    public static <T> FlatFileItemReaderBuilder<T> csvReader(String filePath, Class<T> targetType) {
        return new FlatFileItemReaderBuilder<T>()
                .name(targetType.getSimpleName() + "CsvReader")
                .resource(new FileSystemResource(filePath))
                .delimited(delimited -> delimited.delimiter(DEFAULT_DELIMITER))
                .fieldSetMapper(fieldSetMapper(targetType));
    }

    /**
     * Creates a pre-configured builder with a custom delimiter.
     *
     * @param filePath   the path to the delimited file
     * @param targetType the bean class to map each row to
     * @param delimiter  the field delimiter (e.g., ";" or "\t")
     * @param <T>        the target bean type
     * @return a pre-configured builder ready for further customization
     */
    public static <T> FlatFileItemReaderBuilder<T> csvReader(String filePath, Class<T> targetType, String delimiter) {
        return new FlatFileItemReaderBuilder<T>()
                .name(targetType.getSimpleName() + "CsvReader")
                .resource(new FileSystemResource(filePath))
                .delimited(delimited -> delimited.delimiter(delimiter))
                .fieldSetMapper(fieldSetMapper(targetType));
    }

    /**
     * Creates a pre-configured builder that skips the first line (header row).
     *
     * @param filePath   the path to the CSV file
     * @param targetType the bean class to map each row to
     * @param <T>        the target bean type
     * @return a pre-configured builder with header skipping enabled
     */
    public static <T> FlatFileItemReaderBuilder<T> csvReaderWithHeader(String filePath, Class<T> targetType) {
        return csvReader(filePath, targetType)
                .linesToSkip(1);
    }

    private static <T> BeanWrapperFieldSetMapper<T> fieldSetMapper(Class<T> targetType) {
        var mapper = new BeanWrapperFieldSetMapper<T>();
        mapper.setTargetType(targetType);
        return mapper;
    }
}
