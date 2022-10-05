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
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.jar.Attributes.Name;
import java.util.jar.JarFile;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Handle;

import static org.objectweb.asm.Opcodes.*;
import static org.objectweb.asm.Type.getInternalName;

/**
 * Contains the logic for the obfuscation of artifacts of different formats.
 * <p>
 * {@link InputType#determine(Path)} can be used to retrieve the correct {@code InputType} to be used for the input file
 * located at the provided path.
 */
enum InputType {

    /**
     * Enables the obfuscation of a single {@code .class} files outside the context of a jar file or similar.
     */
    CLASS {
        @Override
        void obfuscate(final InDyObfuscator obfuscator) throws IOException {
            final var arguments = obfuscator.getArguments();
            try (final var inputStream = Files.newInputStream(arguments.getInput())) {
                var reader = new ClassReader(inputStream);
                var writer = new ClassWriter(reader, 0);

                obfuscator.setBootstrapMethodHandle(new Handle(H_INVOKESTATIC, reader.getClassName(),
                    arguments.getBootstrapMethodName(), arguments.getBootstrapMethodDescriptor(), false));

                obfuscator.obfuscateFieldInstructions(reader, writer);

                reader = new ClassReader(writer.toByteArray());
                writer = new ClassWriter(reader, 0);
                obfuscator.obfuscateMethodInstructions(reader, writer);

                reader = new ClassReader(writer.toByteArray());
                writer = new ClassWriter(reader, 0);
                obfuscator.addBootstrapMethod(reader, writer);

                Files.write(arguments.getOutput(), writer.toByteArray());
            }
        }
    },

    /**
     * Enables the obfuscation of a set of {@code .class} files located within a jar file.
     */
    JAR {
        private static final String CLASS_FILE_EXTENSION = ".class";

        @Override
        void obfuscate(final InDyObfuscator obfuscator) throws IOException {
            final var arguments = obfuscator.getArguments();
            Files.copy(arguments.getInput(), arguments.getOutput(), StandardCopyOption.REPLACE_EXISTING);

            try (final var outputFS = FileSystems.newFileSystem(arguments.getOutput())) {
                var bootstrapMethodOwner = getBootstrapMethodOwner(arguments);
                obfuscator.setBootstrapMethodHandle(new Handle(H_INVOKESTATIC, bootstrapMethodOwner,
                    arguments.getBootstrapMethodName(), arguments.getBootstrapMethodDescriptor(), false));

                /*
                 * Do a first pass over the included classes to obfuscate field instructions (GETFIELD, PUTFIELD,
                 * GETSTATIC, PUTSTATIC).
                 */
                transformIncludedClassFiles(obfuscator, outputFS, obfuscator::obfuscateFieldInstructions);

                /*
                 * Do a second pass over the included classes for the main obfuscation step. Synthetic field accessor
                 * methods which might have been generated in the previous step are included.
                 */
                transformIncludedClassFiles(obfuscator, outputFS, obfuscator::obfuscateMethodInstructions);

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

        /**
         * Returns the internal name of the class which should contain the bootstrap method when obfuscating jar file.
         * <p>
         * The owner is derived from either the {@link Arguments#getBootstrapMethodOwner()} command-line argument,
         * the value of the {@code Main-Class} attribute in the input jar file's MANIFEST.MF, or
         *
         * @return The internal name of the class which should contain the bootstrap method.
         *
         * @throws IOException If an I/O error occurs trying to access the input jar file's MANIFEST.MF.
         */
        private static String getBootstrapMethodOwner(final Arguments arguments) throws IOException {
            var owner = arguments.getBootstrapMethodOwner();
            if (owner == null) {
                try (final var jarFile = new JarFile(arguments.getInput().toFile())) {
                    owner = jarFile.getManifest().getMainAttributes().getValue(Name.MAIN_CLASS);
                }
            }
            if (owner == null) {
                return "Bootstrap" + UUID.randomUUID().toString().replace("-", "");
            }
            return owner.replace('.', '/');
        }

        /**
         * Determines the class inside the provided {@code inputJar} which should be the owner of the bootstrap
         * method, modifies or generates that class to include the bootstrap method alongside library loading code,
         * and writes the transformed class to the {@code fileSystem}.
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
            final var bsmOwner     = obfuscator.getBootstrapMethodHandle().getOwner();
            final var bsmOwnerPath = fileSystem.getPath(bsmOwner + CLASS_FILE_EXTENSION);

            // Create BSM owner class if it does not yet exist.
            if (!Files.exists(bsmOwnerPath)) {
                transformIncludedClassFiles(obfuscator, fileSystem, obfuscator::addBootstrapMethodOwnerLoading);

                final var writer = new ClassWriter(0);
                writer.visit(V1_8, ACC_PUBLIC, bsmOwner, null, getInternalName(Object.class), null);
                try (final var bsmOwnerOS = Files.newOutputStream(bsmOwnerPath)) {
                    bsmOwnerOS.write(writer.toByteArray());
                }
            }

            final ClassWriter writer;
            try (final var bsmOwnerIS = Files.newInputStream(bsmOwnerPath)) {
                final var reader = new ClassReader(bsmOwnerIS);
                obfuscator.addBootstrapMethod(reader, (writer = new ClassWriter(reader, 0)));
            }
            // Close InputStream before writing to the FileSystem just to be safe.
            Files.write(bsmOwnerPath, writer.toByteArray());
        }

        /**
         * Walks through the {@code fileSystem}, applying the provided {@code transformation} to all matching class
         * files according to {@link Arguments#matchesIncludePattern(Path)} and writes the result of the transformation
         * back to the file on the filesystem.
         *
         * @param obfuscator The obfuscator containing the parsed {@link Arguments}.
         *
         * @param fileSystem The {@link FileSystem} containing candidate files for obfuscation.
         *
         * @param transformation A transformation to apply to the {@link ClassReader} and {@link ClassWriter} of a
         *                       matched class file.
         *
         * @throws IOException If reading or writing a class file fails.
         */
        private static void transformIncludedClassFiles(final InDyObfuscator                       obfuscator,
                                                        final FileSystem                           fileSystem,
                                                        final BiConsumer<ClassReader, ClassWriter> transformation)
            throws IOException {
            try (final var fileStream = Files.walk(fileSystem.getPath("/"))) {
                fileStream
                    .filter(path -> Files.isRegularFile(path) && path.toString().endsWith(CLASS_FILE_EXTENSION)
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
    };

    private static final Logger LOGGER = System.getLogger(InputType.class.getName());

    /**
     * Obfuscates the input by treating it as the current {@code InputType}.
     *
     * @param obfuscator The obfuscator instance containing the arguments and enabling the execution of basic
     *                   obfuscation steps.
     *
     * @throws IOException If reading the input or writing the result of the obfuscation process fails.
     */
    abstract void obfuscate(InDyObfuscator obfuscator) throws IOException;

    /**
     * Determines the correct {@code InputType} to be used for the file located at the provided {@code path} by checking
     * the file extension or the content of the file.
     *
     * @param path Path of the file that should be used as input for the obfuscation.
     *
     * @return An {@code InputType} capable of obfuscating the file at the {@code path}.
     *
     * @throws IOException If reading the file located at the {@code path} fails.
     */
    public static InputType determine(final Path path) throws IOException {
        try (final var inputStream = new DataInputStream(Files.newInputStream(path))) {
            // Check for the magic number of .class files and treat input as jar file if it is not a .class file.
            return (inputStream.readInt() == 0xCAFEBABE) ? CLASS : JAR;
        }
    }
}
