package dev.blanke.indyobfuscator.template;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Writer;
import java.util.Map;

import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import freemarker.template.Version;

/**
 * Denotes an implementation of the {@link TemplateEngine} interface which uses Apache FreeMarker as backend.
 */
public final class FreeMarkerTemplateEngine implements TemplateEngine {

    private final Configuration configuration;

    public FreeMarkerTemplateEngine() {
        this(Configuration.VERSION_2_3_31);
    }

    public FreeMarkerTemplateEngine(final Version version) {
        configuration = new Configuration(version);
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
