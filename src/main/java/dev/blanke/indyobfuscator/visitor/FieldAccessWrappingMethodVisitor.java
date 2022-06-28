package dev.blanke.indyobfuscator.visitor;

import java.util.stream.Stream;

import org.objectweb.asm.MethodVisitor;

final class FieldAccessWrappingMethodVisitor extends MethodVisitor {

    /**
     * A builder for a stream of {@link FieldAccessorIdentifier}s describing field accessors which will need to be
     * generated.
     *
     * Might contain duplicates that will be eliminated later.
     */
    private final Stream.Builder<FieldAccessorIdentifier> fieldAccessors = Stream.builder();

    FieldAccessWrappingMethodVisitor(final int api, final MethodVisitor methodVisitor) {
        super(api, methodVisitor);
    }

    @Override
    public void visitFieldInsn(final int opcode, final String owner, final String name, final String descriptor) {
        final var identifier = new FieldAccessorIdentifier(opcode, owner, name, descriptor);
        super.visitMethodInsn(
            identifier.getOpcode(), identifier.getOwner(), identifier.getName(), identifier.getDescriptor(), false);
        fieldAccessors.accept(identifier);
    }

    Stream<FieldAccessorIdentifier> fieldAccessors() {
        return fieldAccessors.build();
    }
}
