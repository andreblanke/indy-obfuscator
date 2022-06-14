package dev.blanke.indyobfuscator.visitor;

import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import dev.blanke.indyobfuscator.mapping.SymbolMapping;
import dev.blanke.indyobfuscator.mapping.SymbolMapping.MethodIdentifier;

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

    @Override
    public void visitMethodInsn(final int opcode, final String owner, final String name, final String descriptor,
                                final boolean isInterface) {
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
             *
             * TODO: Check how to proceed with INVOKESPECIAL instructions. Should be grouped along with INVOKEVIRTUAL
             *       and INVOKEINTERFACE (due to access to 'this') but does it really make sense to obfuscate?
             *       See notes in the research paper about constructors.
             */
            case Opcodes.INVOKESPECIAL, Opcodes.INVOKEVIRTUAL, Opcodes.INVOKEINTERFACE -> {
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
        final var invokeDynamicName = symbolMapping.getName(new MethodIdentifier(owner, name, descriptor));
        super.visitInvokeDynamicInsn(invokeDynamicName, invokeDynamicDescriptor, bootstrapMethodHandle);
    }
}
