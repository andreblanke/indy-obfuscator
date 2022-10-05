package dev.blanke.indyobfuscator.obfuscation.method;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

import dev.blanke.indyobfuscator.Obfuscate;
import dev.blanke.indyobfuscator.mapping.MethodInvocation;
import dev.blanke.indyobfuscator.mapping.SymbolMapping;
import dev.blanke.indyobfuscator.obfuscation.ObfuscatingClassVisitor;

import static org.objectweb.asm.Opcodes.*;

public final class MethodInsnObfuscatingClassVisitor extends ObfuscatingClassVisitor {

    private final boolean annotatedOnly;

    private static final String OBFUSCATE_ANNOTATION_DESCRIPTOR = Type.getDescriptor(Obfuscate.class);

    public MethodInsnObfuscatingClassVisitor(final int api, final ClassVisitor classVisitor,
                                             final SymbolMapping symbolMapping, final Handle bootstrapMethodHandle,
                                             final boolean annotatedOnly) {
        super(api, classVisitor, symbolMapping, bootstrapMethodHandle);

        this.annotatedOnly = annotatedOnly;
    }

    @Override
    public MethodVisitor visitMethod(final int access, final String name, final String descriptor,
                                     final String signature, final String[] exceptions) {
        return new MethodInsnObfuscatingMethodVisitor(super.visitMethod(access, name, descriptor, signature, exceptions));
    }

    final class MethodInsnObfuscatingMethodVisitor extends MethodVisitor {

        private boolean annotated;

        private static final System.Logger LOGGER = System.getLogger(MethodInsnObfuscatingMethodVisitor.class.getName());

        MethodInsnObfuscatingMethodVisitor(final MethodVisitor methodVisitor) {
            super(MethodInsnObfuscatingClassVisitor.this.api, methodVisitor);
        }

        @Override
        public AnnotationVisitor visitAnnotation(final String descriptor, final boolean visible) {
            annotated |= OBFUSCATE_ANNOTATION_DESCRIPTOR.equals(descriptor);
            return super.visitAnnotation(descriptor, visible);
        }

        // INVOKEDYNAMIC instructions are not handled by this method.
        @Override
        public void visitMethodInsn(final int opcode, final String owner, final String name, final String descriptor,
                                    final boolean isInterface) {
            if (annotatedOnly && !annotated) {
                super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
                return;
            }

            /*
             * Replacing INVOKESPECIAL instruction invoking <init> with INVOKEDYNAMIC ones would cause bytecode
             * verification to fail.
             *
             * The bytecode verification failures can be circumvented using the -Xverify:none or -noverify JVM
             * command-line arguments. These have, however, been deprecated in Java 13 and will be removed in later
             * versions (see https://bugs.openjdk.org/browse/JDK-8214719).
             *
             * The obfuscation effort is not weakened in a meaningful way by not obfuscating INVOKESPECIAL instructions
             * "due to the fact that a parent class constructor can only have one constructor for the given set of
             * argument types."
             */
            if (name.equals("<init>")) {
                super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
                return;
            }

            if (owner.startsWith("[")) {
                super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
                return;
            }

            /*
             * Adjust the descriptor passed to the invokedynamic instruction depending on the type of invoke* instruction
             * being processed.
             */
            final String invokeDynamicDescriptor;
            switch (opcode) {
                /*
                 * INVOKESTATIC does not allow access to the current object instance via 'this' and can thus use the
                 * original method descriptor.
                 */
                case INVOKESTATIC -> invokeDynamicDescriptor = descriptor;
                /*
                 * All invoke* instructions that allow access to the current object instance via 'this' must have that
                 * parameter added to the descriptor, so that the JVM knows how many values to take off the stack.
                 */
                case INVOKEVIRTUAL, INVOKESPECIAL, INVOKEINTERFACE -> {
                    final String thisType = (opcode == INVOKESPECIAL) ? getClassName() : owner;
                    final String thisTypeDescriptor = (thisType.startsWith("["))
                        // owner is already correct for arrays, i.e. of the form "[Ljava/lang/Object;".
                        ? thisType
                        /*
                         * Otherwise, turn owner of the form "java/lang/Object" into a correct type descriptor of the
                         * form "Ljava/lang/Object;".
                         * Primitives can be disregarded, as one cannot call methods on them.
                         */
                        : "L" + thisType + ";";
                    invokeDynamicDescriptor = '(' + thisTypeDescriptor + descriptor.substring(1);
                }
                default -> {
                    LOGGER.log(System.Logger.Level.WARNING,
                        "Trying to obfuscate method instruction with an unrecognized opcode ({0}). Skipping.", opcode);
                    super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
                    return;
                }
            }
            final var invokeDynamicName =
                symbolMapping.add(new MethodInvocation(opcode, owner, name, descriptor, getClassName()));
            super.visitInvokeDynamicInsn(invokeDynamicName, invokeDynamicDescriptor, bootstrapMethodHandle);
        }
    }
}
