package dev.blanke.indyobfuscator.visitor;

import java.util.Objects;
import java.util.stream.Stream;

import org.objectweb.asm.MethodVisitor;

final class FieldAccessWrappingMethodVisitor extends MethodVisitor {

    /**
     * Name of the class currently being visited which is passed to created {@link FieldAccessorIdentifier} instances.
     *
     * @see #visitFieldInsn(int, String, String, String)
     */
    private final String className;

    /**
     * A builder for a stream of {@link FieldAccessorIdentifier}s describing field accessors which will need to be
     * generated.
     *
     * Might contain duplicates that will be eliminated later.
     */
    private final Stream.Builder<FieldAccessorIdentifier> fieldAccessors = Stream.builder();

    FieldAccessWrappingMethodVisitor(final int api, final MethodVisitor methodVisitor, final String className) {
        super(api, methodVisitor);

        this.className = Objects.requireNonNull(className);
    }

    @Override
    public void visitFieldInsn(final int opcode, final String owner, final String name, final String descriptor) {
        final var identifier = new FieldAccessorIdentifier(className, opcode, owner, name, descriptor);
        super.visitMethodInsn(
            identifier.getOpcode(), identifier.getOwner(), identifier.getName(), identifier.getDescriptor(), false);
        fieldAccessors.accept(identifier);
    }

    Stream<FieldAccessorIdentifier> fieldAccessors() {
        return fieldAccessors.build();
    }
}
