package dev.blanke.indyobfuscator;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.invoke.CallSite;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.jar.Attributes.Name;
import java.util.jar.JarFile;

import org.jetbrains.annotations.Nullable;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import picocli.CommandLine;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import dev.blanke.indyobfuscator.mapping.SequentialSymbolMapping;
import dev.blanke.indyobfuscator.mapping.SymbolMapping;
import dev.blanke.indyobfuscator.visitor.BootstrappingClassVisitor;
import dev.blanke.indyobfuscator.visitor.ObfuscatingClassVisitor;

public final class InDyObfuscator implements Callable<Integer> {

    @Parameters(
        index       = "0",
        description = "The .jar or .class file to be obfuscated.")
    private File input;

    @Option(
        names       = { "-o", "--output" },
        description = "Write obfuscated content to file instead of manipulating input in place")
    private File output;

    @Option(
        names       = { "--bootstrap-method-owner" },
        description = """
            Fully qualified name of a class from the jar file which should contain the bootstrap method.
            Defaults to Main-Class of jar file if unspecified.""",
        paramLabel  = "<fqcn>")
    private String bootstrapMethodOwnerFqcn;

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

    private final SymbolMapping symbolMapping = new SequentialSymbolMapping();

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

    public static void main(final String... args) {
        final int exitCode = new CommandLine(new InDyObfuscator()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() throws Exception {
        return InputType.determine(input).obfuscate(this);
    }

    void addBootstrapMethod(final ClassReader reader, final ClassWriter writer) {
        final var classVisitor = new BootstrappingClassVisitor(Opcodes.ASM9, writer, bootstrapMethodHandle);
        reader.accept(classVisitor, ClassReader.EXPAND_FRAMES);

        bootstrapMethodHandle = classVisitor.getBootstrapMethodHandle();
    }

    byte[] obfuscate(final ClassReader reader, final ClassWriter writer) {
        // Expanded frames are required for LocalVariablesSorter.
        reader.accept(new ObfuscatingClassVisitor(Opcodes.ASM9, writer, symbolMapping, bootstrapMethodHandle),
            ClassReader.EXPAND_FRAMES);
        return writer.toByteArray();
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

    public String getBootstrapMethodOwnerFqcn() {
        return bootstrapMethodOwnerFqcn;
    }

    public Handle getBootstrapMethodHandle() {
        return bootstrapMethodHandle;
    }

    public void setBootstrapMethodHandle(final Handle bootstrapMethodHandle) {
        this.bootstrapMethodHandle = bootstrapMethodHandle;
    }

    private enum InputType {

        CLASS {
            @Override
            int obfuscate(final InDyObfuscator obfuscator) throws IOException {
                final var reader = new ClassReader(new FileInputStream(obfuscator.getInput()));
                final var writer = new ClassWriter(reader, 0);

                obfuscator.setBootstrapMethodHandle(new Handle(Opcodes.H_INVOKESTATIC, reader.getClassName(),
                    BOOTSTRAP_METHOD_DEFAULT_NAME, BOOTSTRAP_METHOD_DESCRIPTOR, false));
                obfuscator.addBootstrapMethod(reader, writer);

                final byte[] classBytes = obfuscator.obfuscate(reader, writer);
                Files.write(obfuscator.getOutput().toPath(), classBytes);
                return 0;
            }
        },

        JAR {
            @Override
            int obfuscate(final InDyObfuscator obfuscator) throws IOException, URISyntaxException {
                final var inputJar = new JarFile(obfuscator.getInput());

                final var outputUri = obfuscator.getOutput().toURI();
                try (final var outputFS = FileSystems.newFileSystem(
                        new URI("jar:" + outputUri.getScheme(), outputUri.getPath(), null), Map.of("create", "true"))) {
                    // Do a first pass over the jar file entries to find the owner of the bootstrap method.
                    if (!addBootstrapMethod(obfuscator, inputJar, outputFS))
                        return 1;

                    // Do a second pass over the jar file entries for the actual obfuscation of classes.
                    obfuscateJarEntries(obfuscator, inputJar, outputFS);
                }
                return 0;
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
             * @return The internal name of the class which should contain the bootstrap method, or {@code null} if
             *         the bootstrap method owner could not be determined due to a missing {@code Main-Class} attribute
             *         and the user not specifying the bootstrap method owner manually using the
             *         {@code --bootstrap-method-owner} command-line option.
             *
             * @throws IOException If an I/O error occurs trying to access the jar file's MANIFEST.MF.
             */
            private @Nullable String getBootstrapMethodOwner(final InDyObfuscator obfuscator, final JarFile jarFile)
                    throws IOException {
                var bootstrapMethodOwnerFqcn = obfuscator.getBootstrapMethodOwnerFqcn();
                if (bootstrapMethodOwnerFqcn == null)
                    bootstrapMethodOwnerFqcn = jarFile.getManifest().getMainAttributes().getValue(Name.MAIN_CLASS);

                return (bootstrapMethodOwnerFqcn != null)
                    ? "L" + bootstrapMethodOwnerFqcn.replace('.', '/') + ";" : null;
            }

            /**
             * Determines the class inside the provided {@code inputJar} which should be the owner of the bootstrap
             * method, modifies that class to include the bootstrap method alongside library loading code, and writes
             * the transformed class to the {@code outputFS}.
             *
             * @param obfuscator The obfuscator instance containing the parsed command-line options along with the
             *                   bootstrap method {@link Handle} describing the bootstrap method to be added.
             *                   The bootstrap method handle will be adjusted by this method via
             *                   {@link InDyObfuscator#setBootstrapMethodHandle(Handle)} if a conflicting method is
             *                   already present.
             *
             * @param inputJar The jar file to be obfuscated. If no owner for the bootstrap method is specified manually
             *                 via the {@code --bootstrap-method-owner} command-line option, the {@code Main-Class}
             *                 manifest attribute will be used to determine the owner, if present.
             *
             * @param outputFS The file system to which the transformed class will be written.
             *
             * @return {@code true} if the owner of the bootstrap method was successfully determined (via either
             *         {@link InDyObfuscator#getBootstrapMethodOwnerFqcn()} or the {@code inputJar}'s {@code Main-Class}
             *         manifest attribute) and the bootstrap method was successfully added to the class, {@code false}
             *         otherwise.
             *
             * @throws IOException If reading from the {@code inputJar} or writing to the {@code outputFS} failed.
             */
            private boolean addBootstrapMethod(final InDyObfuscator obfuscator,
                                               final JarFile        inputJar,
                                               final FileSystem     outputFS) throws IOException {
                final var bootstrapMethodOwner = getBootstrapMethodOwner(obfuscator, inputJar);
                if (bootstrapMethodOwner == null) {
                    System.err.printf("""
                        No '%s' attribute found in MANIFEST.MF.
                        Please specify the bootstrap method owner manually using the --bootstrap-method-owner option.
                        """, Name.MAIN_CLASS);
                    return false;
                }

                obfuscator.setBootstrapMethodHandle(new Handle(Opcodes.H_INVOKESTATIC, bootstrapMethodOwner,
                    BOOTSTRAP_METHOD_DEFAULT_NAME, BOOTSTRAP_METHOD_DESCRIPTOR, false));

                /*
                 * Drop the 'L' prefix, the ';' suffix and append ".class" to convert the internal class name to the
                 * name of the class file.
                 */
                final var bootstrapMethodOwnerJarEntryName =
                    bootstrapMethodOwner.substring(1, bootstrapMethodOwner.length() - 1) + ".class";
                final var bootstrapMethodOwnerJarEntry =
                    inputJar.stream()
                        .filter(entry -> entry.getName().equals(bootstrapMethodOwnerJarEntryName))
                        .findFirst()
                        .orElseThrow();

                final var bootstrapMethodOwnerReader =
                    new ClassReader(inputJar.getInputStream(bootstrapMethodOwnerJarEntry));
                final var bootstrapMethodOwnerWriter = new ClassWriter(bootstrapMethodOwnerReader, 0);
                obfuscator.addBootstrapMethod(bootstrapMethodOwnerReader, bootstrapMethodOwnerWriter);

                final var bootstrapMethodOwnerPath = outputFS.getPath(bootstrapMethodOwnerJarEntryName);
                Files.createDirectories(bootstrapMethodOwnerPath.getParent());
                Files.write(bootstrapMethodOwnerPath, bootstrapMethodOwnerWriter.toByteArray());

                return true;
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

                    if (!entry.getName().endsWith(".class")) {
                        // Copy non-class resources to the outputFS unmodified.
                        Files.copy(inputJar.getInputStream(entry), entryPath, StandardCopyOption.REPLACE_EXISTING);
                        continue;
                    }

                    /*
                     * TODO: Currently, only class files are obfuscated while nested jar files are copied as-is.
                     *       The obfuscation of nested jar files is probably out-of-scope for this proof-of-concept
                     *       implementation.
                     */
                    final var reader = new ClassReader(inputJar.getInputStream(entry));
                    final var writer = new ClassWriter(reader, 0);
                    obfuscator.obfuscate(reader, writer);

                    Files.write(entryPath, writer.toByteArray());
                }
            }
        };

        abstract int obfuscate(InDyObfuscator obfuscator) throws IOException, URISyntaxException;

        public static InputType determine(final File file) throws IOException {
            try (final var inputStream = new DataInputStream(new FileInputStream(file))) {
                // Check for the magic number of .class files and treat input as jar file if it is not a .class file.
                return (inputStream.readInt() == 0xCAFEBABE) ? CLASS : JAR;
            }
        }
    }
}
