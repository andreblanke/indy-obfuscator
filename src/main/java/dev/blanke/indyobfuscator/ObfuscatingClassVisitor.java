package dev.blanke.indyobfuscator;

import java.util.Objects;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;

import dev.blanke.indyobfuscator.mapping.SymbolMapping;

class ObfuscatingClassVisitor extends ClassVisitor {

    private final SymbolMapping symbolMapping;

    private final Handle bootstrapMethodHandle;

    protected ObfuscatingClassVisitor(final int api, final ClassVisitor classVisitor,
                                      final SymbolMapping symbolMapping,
                                      final Handle bootstrapMethodHandle) {
        super(api, classVisitor);

        this.symbolMapping         = Objects.requireNonNull(symbolMapping);
        this.bootstrapMethodHandle = Objects.requireNonNull(bootstrapMethodHandle);
    }

    @Override
    public MethodVisitor visitMethod(final int access, final String name, final String descriptor,
                                     final String signature, final String[] exceptions) {
        return new ObfuscatingMethodVisitor(api, super.visitMethod(access, name, descriptor, signature, exceptions),
            access, name, descriptor, symbolMapping, bootstrapMethodHandle);
    }
}
