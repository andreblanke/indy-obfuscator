package dev.blanke.indyobfuscator.visitor;

import java.util.Objects;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;

import dev.blanke.indyobfuscator.mapping.SymbolMapping;

public final class ObfuscatingClassVisitor extends ClassVisitor {

    private final SymbolMapping symbolMapping;

    private final Handle bootstrapMethodHandle;

    public ObfuscatingClassVisitor(final int api, final ClassVisitor classVisitor, final SymbolMapping symbolMapping,
                                   final Handle bootstrapMethodHandle) {
        super(api, classVisitor);

        this.symbolMapping         = Objects.requireNonNull(symbolMapping);
        this.bootstrapMethodHandle = Objects.requireNonNull(bootstrapMethodHandle);
    }

    @Override
    public void visit(final int version, final int access, final String name, final String signature,
                      final String superName, final String[] interfaces) {
        super.visit(version, access, name, signature, superName, interfaces);
    }

    @Override
    public MethodVisitor visitMethod(final int access, final String name, final String descriptor,
                                     final String signature, final String[] exceptions) {
        return new ObfuscatingMethodVisitor(api, super.visitMethod(access, name, descriptor, signature, exceptions),
            symbolMapping, bootstrapMethodHandle);
    }
}
