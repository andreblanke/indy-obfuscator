package dev.blanke.indyobfuscator.visitor.field;

import java.util.Objects;

import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.commons.GeneratorAdapter;

import static org.objectweb.asm.Opcodes.*;

abstract sealed class FieldAccessorMethodVisitor extends GeneratorAdapter {

    protected final FieldAccess fieldAccessor;

    protected FieldAccessorMethodVisitor(final int api, final MethodVisitor methodVisitor,
                                         final FieldAccess fieldAccessor) {
        super(api, methodVisitor, fieldAccessor.syntheticAccessorInvocationAccess(),
            fieldAccessor.syntheticAccessorInvocation().name(),
            fieldAccessor.syntheticAccessorInvocation().descriptor());
        this.fieldAccessor = Objects.requireNonNull(fieldAccessor);
    }

    /**
     * Visits instructions which are common to both kinds of accessor methods, i.e. delegating to the original field
     * instruction and returning.
     *
     * @implNote Subclasses are expected to prepare the stack accordingly and to invoke this method at the end of their
     *           {@code visitCode()} implementation.
     */
    @Override
    public void visitCode() {
        final var identifier = fieldAccessor.fieldIdentifier();
        visitFieldInsn(fieldAccessor.opcode(), identifier.owner(), identifier.name(), identifier.descriptor());
        returnValue();
    }

    @Override
    public void visitMaxs(int maxStack, int maxLocals) {
        final var isVirtual =
            ((fieldAccessor.opcode() == PUTFIELD) || (fieldAccessor.opcode() == GETFIELD));
        final var isSetter =
            ((fieldAccessor.opcode() == PUTFIELD) || (fieldAccessor.opcode() == PUTSTATIC));

        /*
         * The field accessor takes up at least one stack slot for the setter argument/get* return value,
         * plus an additional slot for the object reference on which the (get|put)field instruction is being executed
         * unless the accessor being generated is static.
         */
        maxStack = Math.max(maxStack, 1 + (isVirtual ? 1 : 0));

        /*
         * The field accessor uses one local variable slot for 'this' unless the field this accessor belongs to is
         * static, plus an additional slot for a setter argument.
         */
        maxLocals = Math.max(maxLocals, (isVirtual ? 1 : 0) + (isSetter ? 1 : 0));

        super.visitMaxs(maxStack, maxLocals);
    }

    static final class FieldGetterMethodVisitor extends FieldAccessorMethodVisitor {

        FieldGetterMethodVisitor(final int api, final MethodVisitor methodVisitor,
                                 final FieldAccess fieldAccessor) {
            super(api, methodVisitor, fieldAccessor);
        }

        @Override
        public void visitCode() {
            // Push instance reference in case the field is an instance member.
            if (fieldAccessor.opcode() == GETFIELD)
                loadArg(0);
            // Invoke original field instruction and return.
            super.visitCode();
        }
    }

    static final class FieldSetterMethodVisitor extends FieldAccessorMethodVisitor {

        FieldSetterMethodVisitor(final int api, final MethodVisitor methodVisitor,
                                 final FieldAccess fieldAccessor) {
            super(api, methodVisitor, fieldAccessor);
        }

        @Override
        public void visitCode() {
            // Push field value or instance reference onto the stack.
            loadArg(0);
            // Push field value in case the field is an instance member.
            if (fieldAccessor.opcode() == PUTFIELD)
                loadArg(1);
            // Invoke original field instruction and return.
            super.visitCode();
        }
    }
}
