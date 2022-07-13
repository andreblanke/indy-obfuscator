package dev.blanke.indyobfuscator;

import java.io.IOException;
import java.lang.invoke.CallSite;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Predicate;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
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
            Regular expression limiting obfuscation to matched fully qualified class name.
            E.g. 'dev\\.blanke\\.indyobfuscator\\..*'.
            """,
        paramLabel = "<regex>")
    private void setIncludePatterns(final List<Pattern> includePatterns) {
        includePatternMatchPredicates = includePatterns.stream().map(Pattern::asMatchPredicate).toList();
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

    //region Bootstrap method options
    @Option(
        names       = { "--bsm-owner", "--bootstrap-method-owner" },
        description = """
            Fully qualified name of a class from the jar file which should contain the bootstrap method.
            Defaults to the Main-Class of a jar file if unspecified. Has no effect if the input is a class file.""",
        paramLabel  = "<fqcn>")
    private String bootstrapMethodOwner;

    /**
     * Returns the internal name of the class which should contain the bootstrap method when obfuscating jar file.
     *
     * The owner is derived from either the {@link #bootstrapMethodOwner} command-line argument or from the value of
     * the {@code Main-Class} attribute in the input jar file's MANIFEST.MF.
     *
     * @return The internal name of the class which should contain the bootstrap method.
     *
     * @throws IOException If an I/O error occurs trying to access the input jar file's MANIFEST.MF.
     *
     * @throws BootstrapMethodOwnerMissingException If the bootstrap method owner could not be determined due
     *                                              to a missing {@code Main-Class} attribute and the user not
     *                                              specifying the bootstrap method owner manually using the
     *                                              {@code --bootstrap-method-owner} command-line option.
     *
     * @see InputType#JAR
     */
    public @NotNull String getBootstrapMethodOwner() throws IOException, BootstrapMethodOwnerMissingException {
        var owner = bootstrapMethodOwner;
        if (owner == null) {
            try (final var jarFile = new JarFile(getInput().toFile())) {
                owner = jarFile.getManifest().getMainAttributes().getValue(Attributes.Name.MAIN_CLASS);
            }
        }
        if (owner == null) {
            throw new BootstrapMethodOwnerMissingException();
        }
        return owner.replace('.', '/');
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
        required    = true,
        description = """
            Template file containing the native bootstrap method implementation.
            The symbol mapping created during obfuscation will be passed to the template as parameter.
            """,
        paramLabel = "<template-file>")
    private Path bootstrapMethodTemplate;

    public @Nullable Path getBootstrapMethodTemplate() {
        return bootstrapMethodTemplate;
    }
    //endregion
}
