package dev.blanke.indyobfuscator.visitor;

import java.util.Objects;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import dev.blanke.indyobfuscator.mapping.SymbolMapping;

public final class ObfuscatingClassVisitor extends ClassVisitor {

    private final SymbolMapping symbolMapping;

    private final Handle bootstrapMethodHandle;

    /**
     * Minimum major version of classes to be able to use {@code invokedynamic} instructions. If a class has a lower
     * major version, it must be changed to a value greater or equal to this one.
     *
     * @see #visit(int, int, String, String, String, String[])
     * @see <a href="https://docs.oracle.com/javase/specs/jvms/se7/jvms7.pdf">JVMS 7</a>
     */
    private static final int MINIMUM_CLASS_VERSION = 51;

    public ObfuscatingClassVisitor(final ClassVisitor classVisitor, final SymbolMapping symbolMapping,
                                   final Handle bootstrapMethodHandle) {
        super(Opcodes.ASM9, classVisitor);

        this.symbolMapping         = Objects.requireNonNull(symbolMapping);
        this.bootstrapMethodHandle = Objects.requireNonNull(bootstrapMethodHandle);
    }

    @Override
    public void visit(final int version, final int access, final String name, final String signature,
                      final String superName, final String[] interfaces) {
        super.visit(Math.max(version, MINIMUM_CLASS_VERSION), access, name, signature, superName, interfaces);
    }

    @Override
    public MethodVisitor visitMethod(final int access, final String name, final String descriptor,
                                     final String signature, final String[] exceptions) {
        return new ObfuscatingMethodVisitor(api, super.visitMethod(access, name, descriptor, signature, exceptions),
            symbolMapping, bootstrapMethodHandle);
    }
}
