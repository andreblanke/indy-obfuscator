package dev.blanke.indyobfuscator.javac;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import javax.tools.FileObject;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.JavaFileObject.Kind;
import javax.tools.SimpleJavaFileObject;

/**
 * A {@link JavaFileManager} implementation which avoids writing to the filesystem and keeps output files entirely
 * in memory. The output files can be accessed using {@link #getOutputFiles()}.
 */
public final class InMemoryJavaFileManager extends ForwardingJavaFileManager<JavaFileManager> {

    private final Map<String, InMemoryJavaFileObject> outputFiles = new HashMap<>();

    // A path is provided so that the SimpleJavaFileObject constructor does not throw an IllegalArgumentException.
    /**
     * The {@link URI} passed to {@link JavaFileObject} implementations used by this class, which is required due to
     * the contract of {@link JavaFileObject#toUri()}.
     *
     * @see CharSequenceJavaFileObject
     * @see InMemoryJavaFileObject
     */
    private static final URI MEMORY_URI = URI.create("memory:///");

    /**
     * @param fileManager The {@link JavaFileManager} to which operations should be delegated.
     */
    public InMemoryJavaFileManager(final JavaFileManager fileManager) {
        super(fileManager);
    }

    @Override
    public JavaFileObject getJavaFileForOutput(final Location location, final String className, final Kind kind,
                                               final FileObject sibling) {
        final var classObject = new InMemoryJavaFileObject(kind);
        outputFiles.put(className, classObject);
        return classObject;
    }

    public Map<String, InMemoryJavaFileObject> getOutputFiles() {
        return outputFiles;
    }

    public static final class CharSequenceJavaFileObject extends SimpleJavaFileObject {

        private final CharSequence charContent;

        public CharSequenceJavaFileObject(final Kind kind, final CharSequence charContent) {
            super(MEMORY_URI, kind);

            this.charContent = Objects.requireNonNull(charContent);
        }

        @Override
        public CharSequence getCharContent(final boolean ignoreEncodingErrors) {
            return charContent;
        }

        @Override
        public boolean isNameCompatible(final String simpleName, final Kind kind) {
            /*
             * Prevent the JavaCompiler from reporting an error if the URI provided to SimpleJavaFileObject does not
             * match the name of the class.
             */
            return true;
        }
    }

    public static final class InMemoryJavaFileObject extends SimpleJavaFileObject {

        private final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        public InMemoryJavaFileObject(final Kind kind) {
            super(MEMORY_URI, kind);
        }

        @Override
        public OutputStream openOutputStream() {
            return outputStream;
        }

        @Override
        public InputStream openInputStream() {
            return new ByteArrayInputStream(outputStream.toByteArray());
        }
    }
}
