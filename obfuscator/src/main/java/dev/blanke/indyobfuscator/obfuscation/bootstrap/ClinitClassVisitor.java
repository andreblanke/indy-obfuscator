package dev.blanke.indyobfuscator.obfuscation.bootstrap;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;

import static org.objectweb.asm.Opcodes.*;

public abstract class ClinitClassVisitor extends ClassVisitor {

    private boolean visitedClinit;

    protected ClinitClassVisitor(final int api, final ClassVisitor visitor) {
        super(api, visitor);
    }

    public abstract MethodVisitor visitClinit(final int access, final String name, final String descriptor,
                                              final String signature, final String[] exceptions,
                                              final MethodVisitor methodVisitor);

    @Override
    public MethodVisitor visitMethod(final int access, final String name, final String descriptor,
                                     final String signature, final String[] exceptions) {
        final boolean isClinit = name.equals("<clinit>");
        visitedClinit |= isClinit;

        final var methodVisitor = super.visitMethod(access, name, descriptor, signature, exceptions);
        if (!isClinit) {
            return methodVisitor;
        }
        return visitClinit(access, name, descriptor, signature, exceptions, methodVisitor);
    }

    @Override
    public void visitEnd() {
        if (!visitedClinit) {
            final var clinitVisitor = visitClinit(ACC_STATIC, "<clinit>", "()V", null, null,
                super.visitMethod(ACC_STATIC, "<clinit>", "()V", null, null));
            clinitVisitor.visitCode();
            clinitVisitor.visitInsn(RETURN);
            clinitVisitor.visitMaxs(0, 0);
            clinitVisitor.visitEnd();
        }
        super.visitEnd();
    }
}
