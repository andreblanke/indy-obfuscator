package dev.blanke.indyobfuscator;

import java.security.ProtectionDomain;
import java.util.Objects;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

final class ClassFileTransformer implements java.lang.instrument.ClassFileTransformer {

    private final Handle obfuscationBootstrapMethodHandle;
    private final Handle replacementBootstrapMethodHandle;

    ClassFileTransformer(final Handle obfuscationBootstrapMethodHandle, final Handle replacementBootstrapMethodHandle) {
        this.obfuscationBootstrapMethodHandle = Objects.requireNonNull(obfuscationBootstrapMethodHandle);
        this.replacementBootstrapMethodHandle = Objects.requireNonNull(replacementBootstrapMethodHandle);
    }

    @Override
    public byte[] transform(final ClassLoader loader, final String className, final Class<?> classBeingRedefined,
                            final ProtectionDomain protectionDomain, final byte[] classfileBuffer) {
        final var reader = new ClassReader(classfileBuffer);
        final var writer = new ClassWriter(reader, 0);
        reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
            @Override
            public MethodVisitor visitMethod(final int access, final String name, final String descriptor,
                                             final String signature, final String[] exceptions) {
                return new MethodVisitor(api, super.visitMethod(access, name, descriptor, signature, exceptions)) {
                    @Override
                    public void visitInvokeDynamicInsn(final String name, final String descriptor,
                                                       final Handle bootstrapMethodHandle,
                                                       final Object... bootstrapMethodArguments) {
                        if (bootstrapMethodHandle.equals(obfuscationBootstrapMethodHandle)) {
                            super.visitInvokeDynamicInsn(name, descriptor,
                                ClassFileTransformer.this.replacementBootstrapMethodHandle, bootstrapMethodArguments);
                            return;
                        }
                        super.visitInvokeDynamicInsn(name, descriptor, bootstrapMethodHandle, bootstrapMethodArguments);
                    }
                };
            }
        }, 0);
        return writer.toByteArray();
    }
}
