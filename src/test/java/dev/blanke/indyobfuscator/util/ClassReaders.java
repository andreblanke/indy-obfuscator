package dev.blanke.indyobfuscator.util;

import java.util.Collections;

import javax.tools.JavaFileObject.Kind;
import javax.tools.ToolProvider;

import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.ClassReader;

import dev.blanke.indyobfuscator.util.InMemoryJavaFileManager.CharSequenceJavaFileObject;

/**
 * A utility class for operations on {@link ClassReader}s.
 *
 * @see ClassReader
 */
public final class ClassReaders {

    private ClassReaders() {
    }

    /**
     * Compiles the provided {@code source} as Java source code using the system Java compiler and returns a
     * {@link ClassReader} for the compiled class.
     *
     * @param source The Java source code for which a {@code ClassReader} should be created.
     *
     * @return A {@code ClassReader} for transforming the compiled Java source code.
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
}
