package dev.blanke.indyobfuscator.visitor;

import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import dev.blanke.indyobfuscator.mapping.MethodIdentifier;
import dev.blanke.indyobfuscator.mapping.SymbolMapping;

final class ObfuscatingMethodVisitor extends MethodVisitor {

    private final SymbolMapping symbolMapping;

    private final Handle bootstrapMethodHandle;

    private static final Logger LOGGER = Logger.getLogger(ObfuscatingMethodVisitor.class.getName());

    ObfuscatingMethodVisitor(final int api, final MethodVisitor methodVisitor, final SymbolMapping symbolMapping,
                             final Handle bootstrapMethodHandle) {
        super(api, methodVisitor);

        this.symbolMapping         = Objects.requireNonNull(symbolMapping);
        this.bootstrapMethodHandle = Objects.requireNonNull(bootstrapMethodHandle);
    }

    // INVOKEDYNAMIC instructions are not handled by this method.
    @Override
    public void visitMethodInsn(final int opcode, final String owner, final String name, final String descriptor,
                                final boolean isInterface) {
        /*
         * TODO: Consider adding an option to opt-in to the obfuscation of <init> INVOKESPECIAL instructions.
         *
         * Replacing INVOKESPECIAL instruction invoking <init> with INVOKEDYNAMIC ones would cause bytecode verification
         * to fail.
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
            case Opcodes.INVOKESTATIC -> invokeDynamicDescriptor = descriptor;
            /*
             * All invoke* instructions that allow access to the current object instance via 'this' must have that
             * parameter added to the descriptor.
             */
            case Opcodes.INVOKEVIRTUAL, Opcodes.INVOKESPECIAL, Opcodes.INVOKEINTERFACE -> {
                final String ownerDescriptor =
                    (owner.startsWith("["))
                        // owner is already correct for arrays, i.e. of the form "[Ljava/lang/Object;".
                        ? owner
                        /*
                         * otherwise, turn owner of the form "java/lang/Object" into a correct type descriptor.
                         * Primitives can be disregarded, as one cannot call methods on them.
                         */
                        : "L" + owner + ";";
                invokeDynamicDescriptor = '(' + ownerDescriptor + descriptor.substring(1);
            }
            default -> {
                LOGGER.log(Level.WARNING, "Trying to obfuscate method with an unrecognized opcode ({0}). Skipping.",
                    opcode);
                super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
                return;
            }
        }
        final var invokeDynamicName = symbolMapping.getName(new MethodIdentifier(owner, name, descriptor, opcode));
        super.visitInvokeDynamicInsn(invokeDynamicName, invokeDynamicDescriptor, bootstrapMethodHandle);
    }
}
