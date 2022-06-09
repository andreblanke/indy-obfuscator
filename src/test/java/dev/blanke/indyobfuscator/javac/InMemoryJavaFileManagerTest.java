package dev.blanke.indyobfuscator.javac;

import java.util.Collections;

import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject.Kind;
import javax.tools.ToolProvider;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.blanke.indyobfuscator.javac.InMemoryJavaFileManager.CharSequenceJavaFileObject;

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
        final var compilationUnits =
            Collections.singleton(new CharSequenceJavaFileObject(Kind.SOURCE, """
                public class Test {
                }
                """));

        final boolean compilationResult = javac.getTask(null, fileManager, null, null, null, compilationUnits).call();
        assertTrue(compilationResult);
        assertFalse(fileManager.getOutputFiles().isEmpty());
    }
}
