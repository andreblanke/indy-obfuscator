package dev.blanke.indyobfuscator.template;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Writer;
import java.util.Map;

import freemarker.template.Configuration;
import freemarker.template.DefaultObjectWrapperBuilder;
import freemarker.template.Template;
import freemarker.template.TemplateException;

import static freemarker.template.Configuration.VERSION_2_3_31;

/**
 * Denotes an implementation of the {@link TemplateEngine} interface which uses Apache FreeMarker as backend.
 */
public final class FreeMarkerTemplateEngine implements TemplateEngine {

    private final Configuration configuration;

    private static final Configuration DEFAULT_CONFIGURATION;

    static {
        final var configuration = new Configuration(VERSION_2_3_31);

        // Enable support for java.lang.Iterable.
        final var objectWrapperBuilder =
            new DefaultObjectWrapperBuilder(configuration.getIncompatibleImprovements());
        objectWrapperBuilder.setIterableSupport(true);
        configuration.setObjectWrapper(objectWrapperBuilder.build());

        // Prevent formatting of numbers.
        configuration.setNumberFormat("computer");

        DEFAULT_CONFIGURATION = configuration;
    }

    public FreeMarkerTemplateEngine() {
        this(DEFAULT_CONFIGURATION);
    }

    public FreeMarkerTemplateEngine(final Configuration configuration) {
        this.configuration = configuration;
    }

    @Override
    public void process(final File template, final DataModel dataModel, final Writer output) throws Exception {
        try (final var reader = new FileReader(template)) {
            process(new Template(template.getName(), reader, configuration), Map.of("dataModel", dataModel), output);
        }
    }

    private void process(final Template template, final Object dataModel, final Writer output)
            throws TemplateException, IOException {
        template.process(dataModel, output);
    }
}
