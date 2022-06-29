package dev.blanke.indyobfuscator;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.function.BiConsumer;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;

/**
 * Contains the logic for the obfuscation of artifacts of different formats.
 */
enum InputType {

    CLASS {
        @Override
        void obfuscate(final InDyObfuscator obfuscator) throws IOException {
            final var arguments = obfuscator.getArguments();
            try (final var inputStream = Files.newInputStream(arguments.getInput())) {
                var reader = new ClassReader(inputStream);
                var writer = new ClassWriter(reader, 0);

                obfuscator.setBootstrapMethodHandle(new Handle(Opcodes.H_INVOKESTATIC, reader.getClassName(),
                    arguments.getBootstrapMethodName(), arguments.getBootstrapMethodDescriptor(), false));

                obfuscator.wrapFieldAccesses(reader, writer);

                reader = new ClassReader(writer.toByteArray());
                writer = new ClassWriter(reader, 0);
                obfuscator.obfuscate(reader, writer);

                reader = new ClassReader(writer.toByteArray());
                writer = new ClassWriter(reader, 0);
                obfuscator.addBootstrapMethod(reader, writer);

                Files.write(arguments.getOutput(), writer.toByteArray());
            }
        }
    },

    JAR {
        @Override
        void obfuscate(final InDyObfuscator obfuscator) throws IOException, BootstrapMethodOwnerMissingException {
            final var arguments = obfuscator.getArguments();
            Files.copy(arguments.getInput(), arguments.getOutput(), StandardCopyOption.REPLACE_EXISTING);

            try (final var outputFS = FileSystems.newFileSystem(arguments.getOutput())) {
                final var bootstrapMethodOwner = arguments.getBootstrapMethodOwner();
                obfuscator.setBootstrapMethodHandle(new Handle(Opcodes.H_INVOKESTATIC, bootstrapMethodOwner,
                    arguments.getBootstrapMethodName(), arguments.getBootstrapMethodDescriptor(), false));

                /*
                 * Do a first pass over the included classes to replace field instructions (GETFIELD, PUTFIELD,
                 * GETSTATIC, PUTSTATIC) with invocations of synthetic getters/setters.
                 */
                transformIncludedClassFiles(obfuscator, outputFS, obfuscator::wrapFieldAccesses);

                /*
                 * Do a second pass over the included classes for the actual obfuscation step. Synthetic field accessor
                 * methods which have been generated in the previous step are included.
                 */
                transformIncludedClassFiles(obfuscator, outputFS, obfuscator::obfuscate);

                /*
                 * Access the jar file entries one more time to add the bootstrap method.
                 *
                 * This needs to be done after the obfuscation pass, as otherwise the library loading code used to set
                 * up the native implementation bootstrap method would be obfuscated as well, resulting in a circular
                 * dependency.
                 */
                addBootstrapMethod(obfuscator, outputFS);
            }
        }

        private static void transformIncludedClassFiles(final InDyObfuscator                       obfuscator,
                                                        final FileSystem                           fileSystem,
                                                        final BiConsumer<ClassReader, ClassWriter> transformation)
                throws IOException {
            try (final var fileStream = Files.walk(fileSystem.getPath("/"))) {
                fileStream
                    .filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".class")
                        && obfuscator.getArguments().matchesIncludePattern(path))
                    .forEach(path -> {
                        try (final var classInputStream = Files.newInputStream(path)) {
                            final var reader = new ClassReader(classInputStream);
                            final var writer = new ClassWriter(reader, 0);

                            LOGGER.log(Level.INFO, "Transforming {0}...", path);
                            transformation.accept(reader, writer);

                            Files.write(path, writer.toByteArray());
                        } catch (final IOException exception) {
                            throw new UncheckedIOException(exception);
                        }
                    });
            } catch (final UncheckedIOException exception) {
                throw exception.getCause(); // Re-throw wrapped original exception.
            }
        }

        /**
         * Determines the class inside the provided {@code inputJar} which should be the owner of the bootstrap
         * method, modifies that class to include the bootstrap method alongside library loading code, and writes
         * the transformed class to the {@code fileSystem}.
         *
         * @param obfuscator The obfuscator instance containing the parsed command-line options along with the
         *                   bootstrap method {@link Handle} describing the bootstrap method to be added.
         *
         * @param fileSystem The file system containing the obfuscated class files along with the already obfuscated
         *                   bootstrap method owner class. The file system serves both as input as well as output
         *                   If no owner for the bootstrap method is specified manually via the
         *                   {@code --bootstrap-method-owner} command-line option, the {@code Main-Class} manifest
         *                   attribute will be used to determine the owner, if present.
         *
         * @throws IOException If reading from the {@code inputJar} or writing to the {@code fileSystem} failed.
         */
        private static void addBootstrapMethod(final InDyObfuscator obfuscator,
                                               final FileSystem     fileSystem) throws IOException {
            final var bootstrapMethodOwnerPath =
                fileSystem.getPath(obfuscator.getBootstrapMethodHandle().getOwner() + ".class");

            final ClassWriter writer;
            try (final var bootstrapMethodOwnerInputStream = Files.newInputStream(bootstrapMethodOwnerPath)) {
                final var reader = new ClassReader(bootstrapMethodOwnerInputStream);
                obfuscator.addBootstrapMethod(reader, (writer = new ClassWriter(reader, 0)));
            }
            // Close InputStream before writing to the FileSystem just to be safe.
            Files.write(bootstrapMethodOwnerPath, writer.toByteArray());
        }
    };

    private static final Logger LOGGER = System.getLogger(InputType.class.getName());

    abstract void obfuscate(InDyObfuscator obfuscator) throws IOException, BootstrapMethodOwnerMissingException;

    public static InputType determine(final Path path) throws IOException {
        try (final var inputStream = new DataInputStream(Files.newInputStream(path))) {
            // Check for the magic number of .class files and treat input as jar file if it is not a .class file.
            return (inputStream.readInt() == 0xCAFEBABE) ? CLASS : JAR;
        }
    }
}
