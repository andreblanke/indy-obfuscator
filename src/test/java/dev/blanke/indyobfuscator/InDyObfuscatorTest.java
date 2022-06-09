package dev.blanke.indyobfuscator;

import java.util.Arrays;

import org.intellij.lang.annotations.Language;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;

import dev.blanke.indyobfuscator.util.ClassReaders;

import static org.junit.jupiter.api.Assertions.*;

final class InDyObfuscatorTest {

    private InDyObfuscator obfuscator;

    @BeforeEach
    void setUp() {
        obfuscator = new InDyObfuscator();
    }

    @Test
    void testObfuscate() {
        @Language("JAVA")
        final var source = """
            class Test {
                public static void main(final String... args) {
                    System.out.println("Hello, world!");
                }
            }
            """;
        final var sourceReader = ClassReaders.forSource(source);

        final var obfuscatedClassNode = new ClassNode();
        new ClassReader(obfuscator.obfuscate(sourceReader).toByteArray()).accept(obfuscatedClassNode, 0);

        final var mainMethodInstructions =
            obfuscatedClassNode.methods.stream()
                .filter(methodNode -> methodNode.name.equals("main"))
                .flatMap(methodNode -> Arrays.stream(methodNode.instructions.toArray()))
                .toList();

        final var invokevirtualInstructions =
            mainMethodInstructions.stream()
                .filter(instruction -> instruction.getOpcode() == Opcodes.INVOKEVIRTUAL)
                .toList();
        assertEquals(0, invokevirtualInstructions.size());

        final var invokedynamicInstructions =
            mainMethodInstructions.stream()
                .filter(instruction -> instruction.getOpcode() == Opcodes.INVOKEDYNAMIC)
                .toList();
        assertEquals(1, invokedynamicInstructions.size());
    }
}
