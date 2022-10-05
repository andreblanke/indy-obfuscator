package dev.blanke.indyobfuscator;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.lang.invoke.CallSite;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import org.objectweb.asm.Type;

import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * Encapsulates the command-line arguments that can be passed to the obfuscator.
 */
public final class Arguments {

    //region Input/output
    @Parameters(
        index       = "0",
        description = "The .jar or .class file to be obfuscated.")
    private Path input;

    public @NotNull Path getInput() {
        return input;
    }

    @Option(
        names       = { "-o", "--output" },
        description = "Write obfuscated content to file instead of manipulating input in place.")
    private Path output;

    /**
     * Returns the {@link Path} to which the obfuscated output should be written.
     *
     * @return {@link #output} if the option was given on the command line, otherwise {@link #input} for in-place
     *         obfuscation.
     */
    public @NotNull Path getOutput() {
        return (output != null) ? output : input;
    }
    //endregion

    //region Includes
    /**
     * A list of predicates matching class names to decide whether the respective classes should be included in the
     * obfuscation process.
     *
     * @see #setIncludePatterns(List)
     */
    private List<Predicate<String>> includePatternMatchPredicates = List.of();

    @Option(
        names       = { "-I", "--include" },
        description = """
            A glob-like pattern to limit obfuscation to matched fully qualified class names.
            E.g. 'dev.blanke.indyobfuscator.*'.
            """,
        paramLabel = "<regex>")
    private void setIncludePatterns(final List<String> includePatterns) {
        includePatternMatchPredicates = includePatterns.stream()
            // Convert glob-like pattern to regex by escaping dots and replacing '*'.
            .map(includePattern -> Pattern.compile(includePattern.replace(".", "\\.").replace("*", ".*")))
            .map(Pattern::asMatchPredicate)
            .toList();
    }

    /**
     * Checks whether the provided path to a class file matches at least one include pattern after conversion to a fully
     * qualified class name, in which case that class will be included in the current obfuscation pass.
     *
     * @param path The path to a class inside a jar file for which inclusion in the obfuscation pass is to be checked.
     *
     * @return {@code true} if the class associated with the {@code path} should be included in the obfuscation pass,
     *         otherwise {@code false}.
     *
     * @see InputType#JAR
     */
    public boolean matchesIncludePattern(final Path path) {
        // Drop the leading slash and convert path separators to dots.
        final var fqcn = path.toString().substring(1).replace('/', '.');
        return includePatternMatchPredicates.isEmpty()
            || includePatternMatchPredicates.stream().anyMatch(predicate -> predicate.test(fqcn));
    }
    //endregion

    @Option(
        names       = { "-a", "--annotated-only" },
        description = """
            Whether obfuscation should be limited to methods annotated with @Obfuscate.
            Disabled by default.""")
    private boolean annotatedOnly;

    public boolean getAnnotatedOnly() {
        return annotatedOnly;
    }

    // Default field value is used for tests only and overridden by Picocli.
    @Option(
        names       = { "-f", "--field-obfuscation-mode" },
        description = """
            Specifies if and how field access instructions (getfield, putfield, getstatic, putstatic) should be obfuscated.
            Valid options are: ${COMPLETION-CANDIDATES}.
            Defaults to ${DEFAULT-VALUE}.""",
        defaultValue = "NONE")
    private FieldObfuscationMode fieldObfuscationMode = FieldObfuscationMode.SYNTHETIC_ACCESSORS;

    public @NotNull FieldObfuscationMode getFieldObfuscationMode() {
        return fieldObfuscationMode;
    }

    public enum FieldObfuscationMode {
        NONE,
        METHOD_HANDLES,
        SYNTHETIC_ACCESSORS
    }

    //region Bootstrap method options
    @Option(
        names       = { "--bsm-owner", "--bootstrap-method-owner" },
        description = """
            Fully qualified name of a class from the jar file which should contain the bootstrap method.
            Defaults to the Main-Class of a jar file if unspecified. Has no effect if the input is a class file.""",
        paramLabel  = "<fqcn>")
    private String bootstrapMethodOwner;

    public @Nullable String getBootstrapMethodOwner() {
        return bootstrapMethodOwner;
    }

    /**
     * @implNote Make sure to update the source code passed to tests when changing the default value.
     */
    @Option(
        names       = { "--bsm-name", "--bootstrap-method-name" },
        description = """
            The name to use for the generated bootstrap method. May have to be changed if the owning class defines a
            conflicting method.
            Defaults to "bootstrap" if unspecified.
            """,
        paramLabel   = "<identifier>")
    private String bootstrapMethodName = "bootstrap";

    public @NotNull String getBootstrapMethodName() {
        return bootstrapMethodName;
    }

    /**
     * The method descriptor specifying the signature of the used bootstrap method.
     */
    private static final String BOOTSTRAP_METHOD_DESCRIPTOR =
        "(" + Type.getDescriptor(MethodHandles.Lookup.class)
            + Type.getDescriptor(String.class)     // invokedName
            + Type.getDescriptor(MethodType.class) // invokedType
            +
        ")" + Type.getDescriptor(CallSite.class);

    public @NotNull String getBootstrapMethodDescriptor() {
        return BOOTSTRAP_METHOD_DESCRIPTOR;
    }

    @Option(
        names       = { "--bsm-template", "--bootstrap-method-template" },
        description = """
            Apache FreeMarker template file containing the native bootstrap method implementation.
            The symbol mapping created during obfuscation will be passed to the template as parameter.
            Defaults to the standard template packaged with the JAR if unspecified.
            """,
        paramLabel = "<file>")
    private Path bootstrapMethodTemplate;

    public @NotNull Reader getBootstrapMethodTemplateReader() {
        if (bootstrapMethodTemplate != null) {
            try {
                return Files.newBufferedReader(bootstrapMethodTemplate);
            } catch (final IOException exception) {
                throw new UncheckedIOException(exception);
            }
        }
        final var templateStream = Objects.requireNonNull(getClass().getResourceAsStream("/bootstrap.c.ftl"));
        return new BufferedReader(new InputStreamReader(templateStream));
    }
    //endregion
}
