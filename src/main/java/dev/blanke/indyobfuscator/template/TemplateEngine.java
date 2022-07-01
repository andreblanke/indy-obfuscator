package dev.blanke.indyobfuscator.template;

import java.io.Writer;
import java.nio.file.Path;

/**
 * A {@code TemplateEngine} allows the combination of a template file with a {@link DataModel} in order to produce an
 * output file.
 */
public interface TemplateEngine {

    /**
     * Combines the provided {@code template} and {@code dataModel}, writing the processed output to the {@code output}
     * {@link Writer}.
     *
     * @param template The template file to populate. Its syntax is implementation-dependent.
     *
     * @param dataModel Encapsulation of fields which can be accessed within the {@code template}.
     *
     * @param output A writer to which the processed output should be written.
     *
     * @throws Exception if an exception occurs reading or populating the {@code template}.
     */
    void process(Path template, DataModel dataModel, Writer output) throws Exception;
}
