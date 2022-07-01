package dev.blanke.indyobfuscator.template;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import freemarker.ext.beans.BeansWrapperBuilder;
import freemarker.template.Configuration;
import freemarker.template.DefaultObjectWrapperBuilder;
import freemarker.template.Template;
import freemarker.template.TemplateException;

import org.objectweb.asm.Opcodes;

import static freemarker.template.Configuration.VERSION_2_3_31;

/**
 * Denotes an implementation of the {@link TemplateEngine} interface which uses Apache FreeMarker as backend.
 *
 * @see <a href="https://freemarker.apache.org/">FreeMarker Java Template Engine</a>
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
    public void process(final Path templatePath, final DataModel dataModel, final Writer output)
            throws IOException, TemplateException {
        try (final var reader = Files.newBufferedReader(templatePath)) {
            final var template = new Template(templatePath.getFileName().toString(), reader, configuration);

            // Allow access to org.objectweb.asm.Opcodes.* from inside the template via the 'Opcodes' variable.
            final var wrapper =
                new BeansWrapperBuilder(template.getConfiguration().getIncompatibleImprovements()).build();
            final var opcodes = wrapper.getStaticModels().get(Opcodes.class.getName());

            template.process(Map.of("dataModel", dataModel, "Opcodes", opcodes), output);
        }
    }
}
