package com.adhar.kit.batch.writer;

import org.springframework.batch.infrastructure.item.file.FlatFileItemWriter;
import org.springframework.batch.infrastructure.item.file.builder.FlatFileItemWriterBuilder;
import org.springframework.core.io.FileSystemResource;

/**
 * Utility for creating CSV file {@link FlatFileItemWriter} instances
 * with common defaults for delimited file writing.
 *
 * <p><b>Usage Example:</b></p>
 * <pre>{@code
 * var writer = CsvItemWriterBuilder.<Order>csvWriter("/output/orders.csv")
 *         .names("id", "customerName", "amount", "status")
 *         .headerCallback(w -> w.write("id,customerName,amount,status"))
 *         .build();
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
public final class CsvItemWriterBuilder {

    private static final String DEFAULT_DELIMITER = ",";

    private CsvItemWriterBuilder() {
        // utility class
    }

    /**
     * Creates a pre-configured {@link FlatFileItemWriterBuilder} for writing CSV files.
     *
     * <p>The builder is initialized with:</p>
     * <ul>
     *   <li>The file resource from the given path</li>
     *   <li>A comma-delimited line aggregator</li>
     *   <li>A writer name based on the file path</li>
     * </ul>
     *
     * @param filePath the path to the output CSV file
     * @param <T>      the item type to write
     * @return a pre-configured builder ready for further customization
     */
    public static <T> FlatFileItemWriterBuilder<T> csvWriter(String filePath) {
        return new FlatFileItemWriterBuilder<T>()
                .name(deriveWriterName(filePath))
                .resource(new FileSystemResource(filePath))
                .delimited(delimited -> delimited.delimiter(DEFAULT_DELIMITER));
    }

    /**
     * Creates a pre-configured builder with a custom delimiter.
     *
     * @param filePath  the path to the output file
     * @param delimiter the field delimiter (e.g., ";" or "\t")
     * @param <T>       the item type to write
     * @return a pre-configured builder ready for further customization
     */
    public static <T> FlatFileItemWriterBuilder<T> csvWriter(String filePath, String delimiter) {
        return new FlatFileItemWriterBuilder<T>()
                .name(deriveWriterName(filePath))
                .resource(new FileSystemResource(filePath))
                .delimited(delimited -> delimited.delimiter(delimiter));
    }

    /**
     * Creates a fully configured {@link FlatFileItemWriter} with field names and a header line.
     *
     * @param filePath   the path to the output CSV file
     * @param header     the header line to write at the top of the file
     * @param fieldNames the bean property names to extract for each line
     * @param <T>        the item type to write
     * @return a configured FlatFileItemWriter
     * @throws Exception if the writer cannot be initialized
     */
    public static <T> FlatFileItemWriter<T> csvWriter(
            String filePath,
            String header,
            String... fieldNames) throws Exception {
        return new FlatFileItemWriterBuilder<T>()
                .name(deriveWriterName(filePath))
                .resource(new FileSystemResource(filePath))
                .delimited(delimited -> delimited.delimiter(DEFAULT_DELIMITER).names(fieldNames))
                .headerCallback(writer -> writer.write(header))
                .build();
    }

    private static String deriveWriterName(String filePath) {
        var fileName = filePath.substring(filePath.lastIndexOf('/') + 1);
        var baseName = fileName.contains(".") ? fileName.substring(0, fileName.lastIndexOf('.')) : fileName;
        return baseName + "CsvWriter";
    }
}
