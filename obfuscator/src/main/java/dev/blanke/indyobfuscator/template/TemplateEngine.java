package dev.blanke.indyobfuscator.template;

import java.io.Reader;
import java.io.Writer;

/**
 * A {@code TemplateEngine} allows the combination of a template file with a {@link DataModel} in order to produce an
 * output file.
 */
public interface TemplateEngine {

    /**
     * Combines the provided {@code template} and {@code dataModel}, writing the processed outputWriter to the
     * {@code outputWriter}.
     *
     * @param templateReader The template file to populate. Its syntax is implementation-dependent.
     *
     * @param dataModel Encapsulation of fields which can be accessed within the {@code template}.
     *
     * @param outputWriter A writer to which the processed output should be written.
     *
     * @throws Exception if an exception occurs reading or populating the {@code template}.
     */
    void process(Reader templateReader, DataModel dataModel, Writer outputWriter) throws Exception;
}
