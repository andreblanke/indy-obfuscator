package dev.blanke.indyobfuscator;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.invoke.CallSite;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.function.Predicate;
import java.util.jar.Attributes.Name;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Pattern;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.util.CheckClassAdapter;

import picocli.CommandLine;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import dev.blanke.indyobfuscator.mapping.SequentialSymbolMapping;
import dev.blanke.indyobfuscator.mapping.SymbolMapping;
import dev.blanke.indyobfuscator.template.DataModel;
import dev.blanke.indyobfuscator.template.FreeMarkerTemplateEngine;
import dev.blanke.indyobfuscator.template.TemplateEngine;
import dev.blanke.indyobfuscator.visitor.BootstrappingClassVisitor;
import dev.blanke.indyobfuscator.visitor.FieldAccessWrappingClassVisitor;
import dev.blanke.indyobfuscator.visitor.ObfuscatingClassVisitor;

public final class InDyObfuscator implements Callable<Integer> {

    @Parameters(
        index       = "0",
        description = "The .jar or .class file to be obfuscated.")
    private File input;

    @Option(
        names       = { "-o", "--output" },
        description = "Write obfuscated content to file instead of manipulating input in place.")
    private File output;

    @Option(
        names       = { "--bsm-name", "--bootstrap-method-name" },
        description = """
            The name to use for the generated bootstrap method. May have to be changed if the owning class defines a
            conflicting method.
            Defaults to "bootstrap" if unspecified.
            """,
        defaultValue = BOOTSTRAP_METHOD_DEFAULT_NAME,
        paramLabel   = "<identifier>")
    private String bootstrapMethodName;

    @Option(
        names       = { "--bsm-owner", "--bootstrap-method-owner" },
        description = """
            Fully qualified name of a class from the jar file which should contain the bootstrap method.
            Defaults to Main-Class of jar file if unspecified.""",
        paramLabel  = "<fqcn>")
    private String bootstrapMethodOwnerFqcn;

    @Option(
        names       = { "--bsm-template", "--bootstrap-method-template" },
        description = """
            Template file containing the native bootstrap method implementation.
            The symbol mapping created during obfuscation will be passed to the template as parameter.
            """,
        paramLabel = "<template-file>")
    private File bootstrapMethodTemplate;

    /** @see #setIncludePatterns(List) */
    private List<Predicate<String>> includePatternMatchPredicates = List.of();

    /**
     * A reference to the bootstrap method to which {@code invokedynamic} instructions delegate.
     *
     * The owner of the bootstrap method depends on the obfuscation taking place: if a class file is being obfuscated,
     * the contained class is also the owner of the bootstrap method. In case a jar file is being obfuscated,
     * the main class will be the owner.
     *
     * @see #BOOTSTRAP_METHOD_DESCRIPTOR
     */
    private Handle bootstrapMethodHandle;

    private final boolean verify;

    private final PrintWriter verificationResultsPrintWriter;

    private final SymbolMapping symbolMapping = new SequentialSymbolMapping();

    private final TemplateEngine templateEngine = new FreeMarkerTemplateEngine();

    /**
     * @implNote Make sure to update the source code passed to tests when changing this value.
     */
    static final String BOOTSTRAP_METHOD_DEFAULT_NAME = "bootstrap";

    /**
     * The method descriptor specifying the signature of the used bootstrap method.
     *
     * @see #bootstrapMethodHandle
     */
    static final String BOOTSTRAP_METHOD_DESCRIPTOR =
        "(" + Type.getDescriptor(MethodHandles.Lookup.class)
            + Type.getDescriptor(String.class)     // invokedName
            + Type.getDescriptor(MethodType.class) // invokedType
            +
        ")" + Type.getDescriptor(CallSite.class);

    InDyObfuscator(final boolean verify) {
        this.verify = verify;

        verificationResultsPrintWriter = verify ? new PrintWriter(System.err) : null;
    }

    public static void main(final String... args) {
        final int exitCode = new CommandLine(new InDyObfuscator(false)).execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() throws Exception {
        try {
            InputType.determine(input).obfuscate(this);

            if (bootstrapMethodTemplate != null) {
                templateEngine.process(bootstrapMethodTemplate, new DataModel(bootstrapMethodHandle, symbolMapping),
                    new PrintWriter(System.out));
            }
            return 0;
        } catch (final BootstrapMethodOwnerMissingException exception) {
            System.err.printf("""
                No '%s' attribute found inside the META-INF/MANIFEST.MF file.
                Please specify the bootstrap method owner manually using the --bootstrap-method-owner option.
                """, Name.MAIN_CLASS);
            return 1;
        } catch (final BootstrapMethodConflictException exception) {
            System.err.printf("""
                The bootstrap method name '%s' conflicts with an existing method inside the owning class '%s'.
                Please specify a different bootstrap method name using the --bootstrap-method-name option.
                """, bootstrapMethodHandle.getName(), bootstrapMethodHandle.getOwner().replace('/', '.'));
            return 2;
        }
    }

    void wrapFieldAccesses(final ClassReader reader, final ClassWriter writer) {
        ClassVisitor visitor = new FieldAccessWrappingClassVisitor(writer);
        if (verify)
            visitor = new CheckClassAdapter(visitor);
        reader.accept(visitor, ClassReader.EXPAND_FRAMES);
        verifyIfEnabled(writer);
    }

    void addBootstrapMethod(final ClassReader reader, final ClassWriter writer) {
        ClassVisitor visitor = new BootstrappingClassVisitor(writer, bootstrapMethodHandle);
        if (verify)
            visitor = new CheckClassAdapter(visitor);
        reader.accept(visitor, ClassReader.EXPAND_FRAMES);
        verifyIfEnabled(writer);
    }

    byte[] obfuscate(final ClassReader reader, final ClassWriter writer) {
        ClassVisitor visitor = new ObfuscatingClassVisitor(writer, symbolMapping, bootstrapMethodHandle);
        if (verify)
            visitor = new CheckClassAdapter(visitor);
        // Expanded frames are required for LocalVariablesSorter.
        reader.accept(visitor, ClassReader.EXPAND_FRAMES);
        verifyIfEnabled(writer);
        return writer.toByteArray();
    }

    private void verifyIfEnabled(final ClassWriter writer) {
        if (verify) {
            CheckClassAdapter.verify(new ClassReader(writer.toByteArray()), true, verificationResultsPrintWriter);
        }
    }

    public File getInput() {
        return input;
    }

    /**
     * Returns the {@link File} to which the obfuscated output should be written.
     *
     * @return {@link #output} if the option was given on the command line, otherwise {@link #input} for in-place
     *         obfuscation.
     */
    public File getOutput() {
        return (output != null) ? output : input;
    }

    public String getBootstrapMethodName() {
        return bootstrapMethodName;
    }

    public String getBootstrapMethodOwnerFqcn() {
        return bootstrapMethodOwnerFqcn;
    }

    public Handle getBootstrapMethodHandle() {
        return bootstrapMethodHandle;
    }

    public void setBootstrapMethodHandle(final Handle bootstrapMethodHandle) {
        this.bootstrapMethodHandle = bootstrapMethodHandle;
    }

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

    public List<Predicate<String>> getIncludePatternMatchPredicates() {
        return includePatternMatchPredicates;
    }

    private enum InputType {

        CLASS {
            @Override
            void obfuscate(final InDyObfuscator obfuscator) throws IOException {
                final var reader = new ClassReader(new FileInputStream(obfuscator.getInput()));
                final var writer = new ClassWriter(reader, 0);

                obfuscator.setBootstrapMethodHandle(new Handle(Opcodes.H_INVOKESTATIC, reader.getClassName(),
                    obfuscator.getBootstrapMethodName(), BOOTSTRAP_METHOD_DESCRIPTOR, false));
                obfuscator.addBootstrapMethod(reader, writer);

                final byte[] classBytes = obfuscator.obfuscate(reader, writer);
                Files.write(obfuscator.getOutput().toPath(), classBytes);
            }
        },

        JAR {
            @Override
            void obfuscate(final InDyObfuscator obfuscator)
                    throws IOException, URISyntaxException, BootstrapMethodOwnerMissingException {
                final var inputJar = new JarFile(obfuscator.getInput());

                final var outputUri = obfuscator.getOutput().toURI();
                try (final var outputFS = FileSystems.newFileSystem(
                        new URI("jar:" + outputUri.getScheme(), outputUri.getPath(), null), Map.of("create", "true"))) {

                    final var bootstrapMethodOwner = getBootstrapMethodOwner(obfuscator, inputJar);
                    obfuscator.setBootstrapMethodHandle(new Handle(Opcodes.H_INVOKESTATIC, bootstrapMethodOwner,
                        obfuscator.getBootstrapMethodName(), BOOTSTRAP_METHOD_DESCRIPTOR, false));

                    // Do a first pass over the jar file entries for the actual obfuscation of classes.
                    obfuscateJarEntries(obfuscator, inputJar, outputFS);

                    /*
                     * Do a second pass over the jar file entries to add the bootstrap method.
                     *
                     * This needs to be done after the first pass, as otherwise the library loading code used to set up
                     * the native implementation bootstrap method will be obfuscated as well, resulting in a circular
                     * dependency.
                     */
                    addBootstrapMethod(obfuscator, outputFS);
                }
            }

            /**
             * Returns the internal name of the class which should contain the bootstrap method, derived from either
             * {@link InDyObfuscator#getBootstrapMethodOwnerFqcn()} or the value of the {@code Main-Class} attribute
             * in the jar file's MANIFEST.MF.
             *
             * @param obfuscator The obfuscator instance containing the parsed command-line options.
             *
             * @param jarFile The jar file to be obfuscated.
             *
             * @return The internal name of the class which should contain the bootstrap method.
             *
             * @throws IOException If an I/O error occurs trying to access the jar file's MANIFEST.MF.
             *
             * @throws BootstrapMethodOwnerMissingException If the bootstrap method owner could not be determined due
             *                                              to a missing {@code Main-Class} attribute and the user not
             *                                              specifying the bootstrap method owner manually using the
             *                                              {@code --bootstrap-method-owner} command-line option.
             */
            private String getBootstrapMethodOwner(final InDyObfuscator obfuscator, final JarFile jarFile)
                throws IOException, BootstrapMethodOwnerMissingException {
                var fqcn = obfuscator.getBootstrapMethodOwnerFqcn();
                if (fqcn == null)
                    fqcn = jarFile.getManifest().getMainAttributes().getValue(Name.MAIN_CLASS);
                if (fqcn == null)
                    throw new BootstrapMethodOwnerMissingException();
                return fqcn.replace('.', '/');
            }

            /**
             * Determines the class inside the provided {@code inputJar} which should be the owner of the bootstrap
             * method, modifies that class to include the bootstrap method alongside library loading code, and writes
             * the transformed class to the {@code outputFS}.
             *
             * @param obfuscator The obfuscator instance containing the parsed command-line options along with the
             *                   bootstrap method {@link Handle} describing the bootstrap method to be added.
             *
             * @param outputFS The file system containing the obfuscated class files along with the already obfuscated
             *                 bootstrap method owner class. The file system serves both as input as well as output
             *                 If no owner for the bootstrap method is specified manually via the
             *                 {@code --bootstrap-method-owner} command-line option, the {@code Main-Class} manifest
             *                 attribute will be used to determine the owner, if present.
             *
             * @throws IOException If reading from the {@code inputJar} or writing to the {@code outputFS} failed.
             */
            private void addBootstrapMethod(final InDyObfuscator obfuscator,
                                            final FileSystem     outputFS) throws IOException {
                final var bootstrapMethodOwnerPath =
                    outputFS.getPath(obfuscator.getBootstrapMethodHandle().getOwner() + ".class");

                final ClassWriter writer;
                try (final var bootstrapMethodOwnerInputStream = Files.newInputStream(bootstrapMethodOwnerPath)) {
                    final var reader = new ClassReader(bootstrapMethodOwnerInputStream);
                    obfuscator.addBootstrapMethod(reader, (writer = new ClassWriter(reader, 0)));
                }
                // Close InputStream before writing to the FileSystem just to be safe.
                Files.write(bootstrapMethodOwnerPath, writer.toByteArray());
            }

            private void obfuscateJarEntries(final InDyObfuscator obfuscator,
                                             final JarFile        inputJar,
                                             final FileSystem     outputFS) throws IOException {
                final var entries = inputJar.entries();

                // Prefer traditional iteration over the Stream API here to allow unchecked exceptions.
                while (entries.hasMoreElements()) {
                    final var entry     = entries.nextElement();
                    final var entryPath = outputFS.getPath(entry.getName());

                    /*
                     * Take no action for directories, as they might be processed after their contained files,
                     * in which case the directory will already have been created inside outputFS.
                     */
                    if (entry.isDirectory())
                        continue;

                    /*
                     * Create the directory which will contain the output files. The parent might be null if the
                     * entry is located in the root of the jar file, in which case no directory needs to be created.
                     */
                    final var parent = entryPath.getParent();
                    if (parent != null)
                        Files.createDirectories(parent);

                    if (!isClass(entry) || !matchesIncludePattern(obfuscator, entry.getName().replace('/', '.'))) {
                        // Copy non-class resources or excluded classes to the outputFS without modification.
                        Files.copy(inputJar.getInputStream(entry), entryPath, StandardCopyOption.REPLACE_EXISTING);
                        continue;
                    }

                    final var reader = new ClassReader(inputJar.getInputStream(entry));
                    final var writer = new ClassWriter(reader, 0);
                    obfuscator.obfuscate(reader, writer);

                    Files.write(entryPath, writer.toByteArray());
                }
            }

            private boolean isClass(final JarEntry entry) {
                return entry.getName().endsWith(".class");
            }

            private boolean matchesIncludePattern(final InDyObfuscator obfuscator, final String fqcn) {
                final var predicates = obfuscator.getIncludePatternMatchPredicates();
                return predicates.isEmpty() || predicates.stream().anyMatch(predicate -> predicate.test(fqcn));
            }
        };

        abstract void obfuscate(InDyObfuscator obfuscator)
            throws IOException, URISyntaxException, BootstrapMethodOwnerMissingException;

        public static InputType determine(final File file) throws IOException {
            try (final var inputStream = new DataInputStream(new FileInputStream(file))) {
                // Check for the magic number of .class files and treat input as jar file if it is not a .class file.
                return (inputStream.readInt() == 0xCAFEBABE) ? CLASS : JAR;
            }
        }
    }
}
