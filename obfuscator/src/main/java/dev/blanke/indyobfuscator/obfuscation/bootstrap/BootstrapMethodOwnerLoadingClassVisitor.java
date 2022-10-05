package dev.blanke.indyobfuscator.obfuscation.bootstrap;

import java.util.Objects;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.commons.GeneratorAdapter;

import static org.objectweb.asm.Type.getType;
import static org.objectweb.asm.commons.Method.getMethod;

public final class BootstrapMethodOwnerLoadingClassVisitor extends ClinitClassVisitor {

    private final Handle bootstrapMethodHandle;

    public BootstrapMethodOwnerLoadingClassVisitor(final int api, final ClassVisitor visitor,
                                                   final Handle bootstrapMethodHandle) {
        super(api, visitor);

        this.bootstrapMethodHandle = Objects.requireNonNull(bootstrapMethodHandle);
    }

    @Override
    public MethodVisitor visitClinit(final int access, final String name, final String descriptor,
                                     final String signature, final String[] exceptions,
                                     final MethodVisitor methodVisitor) {
        return new ClinitMethodVisitor(methodVisitor, access, name, descriptor);
    }

    private final class ClinitMethodVisitor extends GeneratorAdapter {

        private ClinitMethodVisitor(final MethodVisitor methodVisitor, final int access,
                                    final String name, final String descriptor) {
            super(BootstrapMethodOwnerLoadingClassVisitor.this.api, methodVisitor, access, name, descriptor);
        }

        @Override
        public void visitCode() {
            super.visitCode();

            visitLdcInsn(bootstrapMethodHandle.getOwner().replace('/', '.'));
            invokeStatic(getType(Class.class), getMethod("java.lang.Class forName(java.lang.String)"));
            pop();
        }

        @Override
        public void visitMaxs(final int maxStack, final int maxLocals) {
            super.visitMaxs(Math.max(maxStack, 1), maxLocals);
        }
    }
}
