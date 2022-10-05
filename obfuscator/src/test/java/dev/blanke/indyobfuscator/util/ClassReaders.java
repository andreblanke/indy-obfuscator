package dev.blanke.indyobfuscator.util;

import java.util.Collections;
import java.util.Objects;
import java.util.function.BiConsumer;

import javax.tools.JavaFileObject.Kind;
import javax.tools.ToolProvider;

import org.jetbrains.annotations.Nullable;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;

import dev.blanke.indyobfuscator.util.InMemoryJavaFileManager.CharSequenceJavaFileObject;

/**
 * A utility class for operations on {@link ClassReader}s.
 *
 * @see ClassReader
 */
public final class ClassReaders {

    // Prevent instantiation of utility class.
    private ClassReaders() {
    }

    /**
     * Compiles the provided {@code source} as Java source code using the system Java compiler and returns a
     * {@link ClassReader} for the compiled class.
     *
     * @param source The Java source code for which a {@code ClassReader} should be created.
     *
     * @return A {@code ClassReader} for transforming the compiled Java source code or {@code null} if the source code
     *         failed to compile.
     */
    public static @Nullable ClassReader forSource(final String source) {
        final var javac       = ToolProvider.getSystemJavaCompiler();
        final var fileManager = new InMemoryJavaFileManager(javac.getStandardFileManager(null, null, null));
        final var compilationUnits = Collections.singleton(new CharSequenceJavaFileObject(Kind.SOURCE, source));

        final boolean compilationResult = javac.getTask(null, fileManager, null, null, null, compilationUnits).call();
        if (!compilationResult)
            return null;

        final var outputFiles = fileManager.getOutputFiles().values();
        if (outputFiles.size() != 1)
            throw new IllegalStateException();

        final var classFile = outputFiles.stream().findFirst().get();
        return new ClassReader(classFile.openInputStream().readAllBytes());
    }

    /**
     * Compiles the provided {@code source} as Java source code using {@link #forSource(String)}, applies the
     * transformation to a {@link ClassReader} and {@link ClassWriter} created from the compiled class, and returns a
     * {@link ClassNode} to inspect the transformation result.
     *
     * @param source The Java source code which should be compiled and whose bytecode should be transformed via the
     *               supplied transformation.
     *
     * @param transformation A transformation to modify the bytecode of the compiled Java source code by operating on
     *                       the passed {@link ClassReader} and {@link ClassWriter}.
     *
     * @return A {@link ClassNode} to allow the inspection of the transformation result.
     *
     * @see #forSource(String)
     */
    public static ClassNode compileAndTransform(final String source,
                                                final BiConsumer<ClassReader, ClassWriter> transformation) {
        final var reader = Objects.requireNonNull(forSource(source));
        final var writer = new ClassWriter(reader, ClassWriter.COMPUTE_FRAMES);
        transformation.accept(reader, writer);

        final var transformedClassNode = new ClassNode();
        new ClassReader(writer.toByteArray()).accept(transformedClassNode, 0);
        return transformedClassNode;
    }
}
