package dev.blanke.indyobfuscator;

import java.util.Arrays;

import org.intellij.lang.annotations.Language;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;

import dev.blanke.indyobfuscator.util.ClassReaders;

import static org.junit.jupiter.api.Assertions.*;

final class InDyObfuscatorTest {

    private InDyObfuscator obfuscator;

    @BeforeEach
    void setUp() {
        obfuscator = new InDyObfuscator();
        obfuscator.setBootstrapMethodHandle(
            new Handle(Opcodes.H_INVOKESTATIC, "", InDyObfuscator.BOOTSTRAP_METHOD_DEFAULT_NAME,
                InDyObfuscator.BOOTSTRAP_METHOD_DESCRIPTOR, false));
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
        final var obfuscatedClassNode = ClassReaders.compileAndTransform(source, obfuscator::obfuscate);

        final var mainMethodInstructions =
            obfuscatedClassNode.methods.stream()
                .filter(methodNode -> methodNode.name.equals("main"))
                .flatMap(methodNode -> Arrays.stream(methodNode.instructions.toArray()))
                .toList();

        /*
         * Assert that no invokevirtual instructions remain after the obfuscation, despite one being present in the
         * source code above (println).
         */
        final var invokevirtualInstructions =
            mainMethodInstructions.stream()
                .filter(instruction -> instruction.getOpcode() == Opcodes.INVOKEVIRTUAL)
                .toList();
        assertEquals(0, invokevirtualInstructions.size());

        // Assert that an invokedynamic instruction now exists which should not exist in the above source code.
        final var invokedynamicInstructions =
            mainMethodInstructions.stream()
                .filter(instruction -> instruction.getOpcode() == Opcodes.INVOKEDYNAMIC)
                .toList();
        assertEquals(1, invokedynamicInstructions.size());

        // Assert that the generated invokedynamic instruction uses the method handle configured in setUp.
        final var invokedynamicInstruction = (InvokeDynamicInsnNode) invokedynamicInstructions.get(0);
        assertEquals(obfuscator.getBootstrapMethodHandle(), invokedynamicInstruction.bsm);
    }

    @Test
    void testAddBootstrapMethodCreateClinit() {
        @Language("JAVA")
        final var source = """
            class Test {
            }
            """;
        final var bootstrapClassNode = ClassReaders.compileAndTransform(source, obfuscator::addBootstrapMethod);

        // Assert that <clinit> has been created.
        final var clinitMethods =
            bootstrapClassNode.methods.stream()
                .filter(methodNode -> methodNode.name.equals("<clinit>"))
                .toList();
        assertEquals(1, clinitMethods.size());
        assertLoadMethodInstructionExists(clinitMethods.get(0).instructions);

        assertBootstrapMethodExists(bootstrapClassNode, obfuscator.getBootstrapMethodHandle());
    }

    @Test
    void testAddBootstrapMethodModifyClinit() {
        @Language("JAVA")
        final var source = """
            class Test {
                static {
                    System.out.println("Hello, world!");
                }
            }
            """;
        final var bootstrapClassNode = ClassReaders.compileAndTransform(source, obfuscator::addBootstrapMethod);

        // Assert that <clinit> has been modified instead of an additional one having been created.
        final var clinitMethods =
            bootstrapClassNode.methods.stream()
                .filter(methodNode -> methodNode.name.equals("<clinit>"))
                .toList();
        assertEquals(1, clinitMethods.size());
        assertLoadMethodInstructionExists(clinitMethods.get(0).instructions);

        assertBootstrapMethodExists(bootstrapClassNode, obfuscator.getBootstrapMethodHandle());
    }

    @Test
    void testAddBootstrapMethodConflictingMethod() {
        @Language("JAVA")
        final var source = """
            import java.lang.invoke.CallSite;
            import java.lang.invoke.MethodHandles;
            import java.lang.invoke.MethodType;

            class Test {
                static CallSite bootstrap(MethodHandles.Lookup lookup, String invokedName, MethodType invokedType) {
                    return null;
                }
            }
            """;
        assertThrows(BootstrapMethodConflictException.class, () ->
            ClassReaders.compileAndTransform(source, obfuscator::addBootstrapMethod));
    }

    private static void assertLoadMethodInstructionExists(final InsnList instructions) {
        final var loadMethodInstructions =
            Arrays.stream(instructions.toArray())
                .filter(instruction -> instruction.getOpcode() == Opcodes.INVOKESTATIC)
                .map(MethodInsnNode.class::cast)
                .filter(instruction -> instruction.owner.equals("java/lang/System")
                    && (instruction.name.equals("load")))
                .toList();
        assertTrue(loadMethodInstructions.size() >= 1);
    }

    private static void assertBootstrapMethodExists(final ClassNode classNode, final Handle bootstrapMethodHandle) {
        // Assert that a bootstrap method with correct name and descriptor has been created.
        final var bootstrapMethods =
            classNode.methods.stream()
                .filter(methodNode ->
                    methodNode.name.equals(bootstrapMethodHandle.getName()) &&
                    methodNode.desc.equals(bootstrapMethodHandle.getDesc()))
                .toList();
        assertEquals(1, bootstrapMethods.size());

        // Assert that the method's access flags match the ones set by the BootstrappingClassVisitor.
        final var bootstrapMethod = bootstrapMethods.get(0);
        assertEquals(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_NATIVE | Opcodes.ACC_SYNTHETIC,
            bootstrapMethod.access);
    }
}
