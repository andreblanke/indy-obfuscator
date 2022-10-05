package dev.blanke.indyobfuscator.util;

import java.util.Collections;

import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject.Kind;
import javax.tools.ToolProvider;

import org.intellij.lang.annotations.Language;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.blanke.indyobfuscator.util.InMemoryJavaFileManager.CharSequenceJavaFileObject;

import static org.junit.jupiter.api.Assertions.*;

final class InMemoryJavaFileManagerTest {

    private InMemoryJavaFileManager fileManager;

    private static JavaCompiler javac;

    @BeforeAll
    static void setUpClass() {
        javac = ToolProvider.getSystemJavaCompiler();
    }

    @BeforeEach
    void setUp() {
        // Re-create fileManager for each invocation to start with empty outputFiles map.
        fileManager = new InMemoryJavaFileManager(javac.getStandardFileManager(null, null, null));
    }

    @Test
    void testCompile() {
        @Language("JAVA")
        final var source = """
            class Test {
            }
            """;
        final var compilationUnits = Collections.singleton(new CharSequenceJavaFileObject(Kind.SOURCE, source));

        final boolean compilationResult = javac.getTask(null, fileManager, null, null, null, compilationUnits).call();
        assertTrue(compilationResult);
        assertEquals(1, fileManager.getOutputFiles().size());
    }
}
