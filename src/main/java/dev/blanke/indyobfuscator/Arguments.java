package dev.blanke.indyobfuscator;

import java.io.File;
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

    @Parameters(
        index       = "0",
        description = "The .jar or .class file to be obfuscated.")
    private Path input;

    @Option(
        names       = { "-o", "--output" },
        description = "Write obfuscated content to file instead of manipulating input in place.")
    private Path output;

    /**
     * A list of predicates matching class names to include in the obfuscation process.
     *
     * @see #setIncludePatterns(List)
     */
    private List<Predicate<String>> includePatternMatchPredicates = List.of();

    @Option(
        names       = { "--bsm-owner", "--bootstrap-method-owner" },
        description = """
            Fully qualified name of a class from the jar file which should contain the bootstrap method.
            Defaults to Main-Class of jar file if unspecified.""",
        paramLabel  = "<fqcn>")
    private String bootstrapMethodOwner;

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

    @Option(
        names       = { "--bsm-template", "--bootstrap-method-template" },
        description = """
            Template file containing the native bootstrap method implementation.
            The symbol mapping created during obfuscation will be passed to the template as parameter.
            """,
        paramLabel = "<template-file>")
    private File bootstrapMethodTemplate;

    /**
     * The method descriptor specifying the signature of the used bootstrap method.
     */
    private static final String BOOTSTRAP_METHOD_DESCRIPTOR =
        "(" + Type.getDescriptor(MethodHandles.Lookup.class)
            + Type.getDescriptor(String.class)     // invokedName
            + Type.getDescriptor(MethodType.class) // invokedType
            +
        ")" + Type.getDescriptor(CallSite.class);

    @Option(
        names       = { "-I", "--include" },
        description = """
            Regular expression limiting obfuscation to matched fully qualified class name.
            E.g. 'dev\\.blanke\\.indyobfuscator\\.*'.
            """,
        paramLabel = "<regex>")
    private void setIncludePatterns(final List<Pattern> includePatterns) {
        includePatternMatchPredicates = includePatterns.stream().map(Pattern::asMatchPredicate).toList();
    }

    public @NotNull List<Predicate<String>> getIncludePatternMatchPredicates() {
        return includePatternMatchPredicates;
    }

    public @NotNull Path getInput() {
        return input;
    }

    /**
     * Returns the {@link Path} to which the obfuscated output should be written.
     *
     * @return {@link #output} if the option was given on the command line, otherwise {@link #input} for in-place
     *         obfuscation.
     */
    public @NotNull Path getOutput() {
        return (output != null) ? output : input;
    }

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

    public @NotNull String getBootstrapMethodName() {
        return bootstrapMethodName;
    }

    public @NotNull String getBootstrapMethodDescriptor() {
        return BOOTSTRAP_METHOD_DESCRIPTOR;
    }

    public @Nullable File getBootstrapMethodTemplate() {
        return bootstrapMethodTemplate;
    }
}
