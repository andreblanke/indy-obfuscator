package dev.blanke.indyobfuscator;

import java.util.Arrays;
import java.util.function.Predicate;

import org.intellij.lang.annotations.Language;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import org.objectweb.asm.Handle;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import dev.blanke.indyobfuscator.util.ClassReaders;

import static org.junit.jupiter.api.Assertions.*;

import static org.objectweb.asm.Opcodes.*;

final class InDyObfuscatorTest {

    private InDyObfuscator obfuscator;

    @BeforeEach
    void setUp() {
        obfuscator = new InDyObfuscator(true);
        obfuscator.setBootstrapMethodHandle(
            new Handle(H_INVOKESTATIC, "", InDyObfuscator.BOOTSTRAP_METHOD_DEFAULT_NAME,
                InDyObfuscator.BOOTSTRAP_METHOD_DESCRIPTOR, false));
    }

    private static MethodNode assertSingleClinitMethodExists(final ClassNode classNode) {
        final var clinitMethods =
            classNode.methods.stream()
                .filter(methodNode -> methodNode.name.equals("<clinit>"))
                .toList();
        assertEquals(1, clinitMethods.size());
        return clinitMethods.get(0);
    }

    private static void assertMethodInstructionExists(final InsnList                  instructions,
                                                      final Predicate<MethodInsnNode> predicate) {
        assertInstructionExists(instructions, instruction ->
            (instruction instanceof MethodInsnNode methodInstruction) && predicate.test(methodInstruction));
    }

    private static void assertInstructionExists(final InsnList                    instructions,
                                                final Predicate<AbstractInsnNode> predicate) {
        final var instructions0 =
            Arrays.stream(instructions.toArray())
                .filter(predicate)
                .toList();
        assertTrue(instructions0.size() >= 1);
    }

    @Nested
    final class WrapFieldAccesses {

        @Test
        void testWrapFieldAccessesGetField() {
            @Language("JAVA")
            final var source = """
                class Test {
                    private int x = 0;

                    {
                        System.exit(x);
                    }
                }
                """;
            final var fieldWrappedClassNode = ClassReaders.compileAndTransform(source, obfuscator::wrapFieldAccesses);

            final var clinitMethod = assertSingleClinitMethodExists(fieldWrappedClassNode);
        }

        @Test
        void testWrapFieldAccessesPutField() {
            @Language("JAVA")
            final var source = """
                class Test {
                    private int x = 0;
                }
                """;
            final var fieldWrappedClassNode = ClassReaders.compileAndTransform(source, obfuscator::wrapFieldAccesses);

            final var clinitMethod = assertSingleClinitMethodExists(fieldWrappedClassNode);
        }

        @Test
        void testWrapFieldAccessesGetStatic() {
            @Language("JAVA")
            final var source = """
            class Test {
                static int x = 0;

                static {
                    System.exit(x);
                }
            }
            """;
            final var fieldWrappedClassNode = ClassReaders.compileAndTransform(source, obfuscator::wrapFieldAccesses);

            final var clinitMethod = assertSingleClinitMethodExists(fieldWrappedClassNode);
        }

        @Test
        void testWrapFieldAccessesPutStatic() {
            @Language("JAVA")
            final var source = """
            class Test {
                static int x = 0;
            }
            """;
            final var fieldWrappedClassNode = ClassReaders.compileAndTransform(source, obfuscator::wrapFieldAccesses);

            final var clinitMethod = assertSingleClinitMethodExists(fieldWrappedClassNode);
        }
    }

    @Nested
    final class AddBootstrapMethod {

        @Test
        void testAddBootstrapMethodCreateClinit() {
            @Language("JAVA")
            final var source = """
            class Test {
            }
            """;
            final var bootstrapClassNode = ClassReaders.compileAndTransform(source, obfuscator::addBootstrapMethod);

            // Assert that <clinit> has been created.
            final var clinitMethod = assertSingleClinitMethodExists(bootstrapClassNode);
            assertLoadMethodInstructionExists(clinitMethod.instructions);

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
            final var clinitMethod = assertSingleClinitMethodExists(bootstrapClassNode);

            assertLoadMethodInstructionExists(clinitMethod.instructions);
            assertMethodInstructionExists(clinitMethod.instructions, instruction ->
                (instruction.getOpcode() == INVOKEVIRTUAL) && (instruction.owner.equals("java/io/PrintStream"))
                    && (instruction.name.equals("println")));

            assertBootstrapMethodExists(bootstrapClassNode, obfuscator.getBootstrapMethodHandle());
        }

        private static void assertLoadMethodInstructionExists(final InsnList instructions) {
            assertMethodInstructionExists(instructions, instruction -> (instruction.getOpcode() == INVOKESTATIC)
                && (instruction.owner.equals("java/lang/System")) && (instruction.name.equals("load")));
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
            assertEquals(ACC_PUBLIC | ACC_STATIC | ACC_NATIVE | ACC_SYNTHETIC, bootstrapMethod.access);
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
    }

    @Nested
    final class Obfuscate {
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
                    .filter(instruction -> instruction.getOpcode() == INVOKEVIRTUAL)
                    .toList();
            assertEquals(0, invokevirtualInstructions.size());

            // Assert that an invokedynamic instruction now exists which should not exist in the above source code.
            final var invokedynamicInstructions =
                mainMethodInstructions.stream()
                    .filter(instruction -> instruction.getOpcode() == INVOKEDYNAMIC)
                    .toList();
            assertEquals(1, invokedynamicInstructions.size());

            // Assert that the generated invokedynamic instruction uses the method handle configured in setUp.
            final var invokedynamicInstruction = (InvokeDynamicInsnNode) invokedynamicInstructions.get(0);
            assertEquals(obfuscator.getBootstrapMethodHandle(), invokedynamicInstruction.bsm);
        }
    }
}
