package dev.blanke.indyobfuscator;

import java.util.Arrays;
import java.util.List;
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

import dev.blanke.indyobfuscator.visitor.bootstrap.BootstrapMethodConflictException;

import static org.junit.jupiter.api.Assertions.*;

import static org.objectweb.asm.Opcodes.*;

import static dev.blanke.indyobfuscator.util.ClassReaders.compileAndTransform;

final class InDyObfuscatorTest {

    private InDyObfuscator obfuscator;

    @BeforeEach
    void setUp() {
        final var arguments = new Arguments();
        obfuscator = new InDyObfuscator(true);
        obfuscator.setBootstrapMethodHandle(new Handle(H_INVOKESTATIC, "",
            arguments.getBootstrapMethodName(), arguments.getBootstrapMethodDescriptor(), false));
    }

    private static MethodNode assertClinitExists(final ClassNode classNode) {
        return assertMethodExists(classNode, method -> method.name.equals("<clinit>"));
    }

    private static MethodNode assertMethodExists(final ClassNode classNode, final Predicate<MethodNode> predicate) {
        final var methods = classNode.methods.stream().filter(predicate).toList();
        assertEquals(1, methods.size());
        return methods.get(0);
    }

    private static void assertMethodInstructionExists(final InsnList                  instructions,
                                                      final Predicate<MethodInsnNode> predicate) {
        assertInstructionExists(instructions, instruction ->
            (instruction instanceof MethodInsnNode methodInstruction) && predicate.test(methodInstruction));
    }

    private static void assertInstructionExists(final InsnList                    instructions,
                                                final Predicate<AbstractInsnNode> predicate) {
        assertFalse(findInstructions(instructions, predicate).isEmpty());
    }

    private static void assertInstructionNotExists(final InsnList                    instructions,
                                                   final Predicate<AbstractInsnNode> predicate) {
        assertTrue(findInstructions(instructions, predicate).isEmpty());
    }

    private static List<AbstractInsnNode> findInstructions(final InsnList                    instructions,
                                                           final Predicate<AbstractInsnNode> predicate) {
        return Arrays.stream(instructions.toArray()).filter(predicate).toList();
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
            final var classNode = compileAndTransform(source, obfuscator::wrapFieldAccesses);

            final var init = assertMethodExists(classNode, method -> method.name.equals("<init>"));
            assertInstructionNotExists(init.instructions, instruction -> instruction.getOpcode() == GETFIELD);

            final var syntheticGetter = assertMethodExists(classNode, method -> method.name.startsWith("x")
                && method.desc.equals("(LTest;)I") && ((method.access & ACC_STATIC) != 0));
            assertInstructionExists(syntheticGetter.instructions, instruction -> instruction.getOpcode() == GETFIELD);
        }

        @Test
        void testWrapFieldAccessesPutField() {
            @Language("JAVA")
            final var source = """
                class Test {
                    private int x = 0;
                }
                """;
            final var classNode = compileAndTransform(source, obfuscator::wrapFieldAccesses);

            final var init = assertMethodExists(classNode, method -> method.name.equals("<init>"));
            assertInstructionNotExists(init.instructions, instruction -> instruction.getOpcode() == PUTFIELD);

            final var syntheticSetter = assertMethodExists(classNode, method -> method.name.startsWith("x")
                && method.desc.equals("(LTest;I)V") && ((method.access & ACC_STATIC) != 0));
            assertInstructionExists(syntheticSetter.instructions, instruction -> instruction.getOpcode() == PUTFIELD);
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
            final var classNode = compileAndTransform(source, obfuscator::wrapFieldAccesses);

            final var clinit = assertClinitExists(classNode);
            assertInstructionNotExists(clinit.instructions, instruction -> instruction.getOpcode() == GETSTATIC);

            final var syntheticGetter = assertMethodExists(classNode, method -> method.name.startsWith("x")
                && method.desc.equals("()I") && ((method.access & ACC_STATIC) != 0));
            assertInstructionExists(syntheticGetter.instructions, instruction -> instruction.getOpcode() == GETSTATIC);
        }

        @Test
        void testWrapFieldAccessesPutStatic() {
            @Language("JAVA")
            final var source = """
                class Test {
                    static int x = 0;
                }
                """;
            final var classNode = compileAndTransform(source, obfuscator::wrapFieldAccesses);

            final var clinit = assertClinitExists(classNode);
            assertInstructionNotExists(clinit.instructions, instruction -> instruction.getOpcode() == PUTSTATIC);

            final var syntheticGetter = assertMethodExists(classNode, method -> method.name.startsWith("x")
                && method.desc.equals("(I)V") && ((method.access & ACC_STATIC) != 0));
            assertInstructionExists(syntheticGetter.instructions, instruction -> instruction.getOpcode() == PUTSTATIC);
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
            final var classNode = compileAndTransform(source, obfuscator::addBootstrapMethod);

            // Assert that <clinit> has been created.
            final var clinitMethod = assertClinitExists(classNode);
            assertLoadMethodInstructionExists(clinitMethod.instructions);

            assertBootstrapMethodExists(classNode, obfuscator.getBootstrapMethodHandle());
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
            final var classNode = compileAndTransform(source, obfuscator::addBootstrapMethod);

            // Assert that <clinit> has been modified instead of an additional one having been created.
            final var clinit = assertClinitExists(classNode);

            assertLoadMethodInstructionExists(clinit.instructions);
            assertMethodInstructionExists(clinit.instructions, instruction ->
                (instruction.getOpcode() == INVOKEVIRTUAL) && (instruction.owner.equals("java/io/PrintStream"))
                    && (instruction.name.equals("println")));

            assertBootstrapMethodExists(classNode, obfuscator.getBootstrapMethodHandle());
        }

        private static void assertLoadMethodInstructionExists(final InsnList instructions) {
            assertMethodInstructionExists(instructions, instruction -> (instruction.getOpcode() == INVOKESTATIC)
                && (instruction.owner.equals("java/lang/System")) && (instruction.name.equals("load")));
        }

        private static void assertBootstrapMethodExists(final ClassNode classNode, final Handle bootstrapMethodHandle) {
            // Assert that a bootstrap method with correct name and descriptor has been created.
            final var bootstrapMethods =
                classNode.methods.stream()
                    .filter(method -> method.name.equals(bootstrapMethodHandle.getName())
                            && method.desc.equals(bootstrapMethodHandle.getDesc()))
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
                compileAndTransform(source, obfuscator::addBootstrapMethod));
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
            final var classNode = compileAndTransform(source, obfuscator::obfuscate);

            final var mainMethodInstructions =
                classNode.methods.stream()
                    .filter(method -> method.name.equals("main"))
                    .flatMap(method -> Arrays.stream(method.instructions.toArray()))
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
