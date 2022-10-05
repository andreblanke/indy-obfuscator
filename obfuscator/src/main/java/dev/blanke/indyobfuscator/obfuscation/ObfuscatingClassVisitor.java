package dev.blanke.indyobfuscator.obfuscation;

import java.util.Objects;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Handle;

import dev.blanke.indyobfuscator.mapping.SymbolMapping;

public abstract class ObfuscatingClassVisitor extends ClassVisitor {

    private String className;

    protected final SymbolMapping symbolMapping;

    protected final Handle bootstrapMethodHandle;

    /**
     * Minimum major version of classes to be able to use {@code invokedynamic} instructions. If a class has a lower
     * major version, it must be changed to a value greater or equal to this one.
     *
     * @see #visit(int, int, String, String, String, String[])
     * @see <a href="https://docs.oracle.com/javase/specs/jvms/se7/jvms7.pdf">JVMS 7</a>
     */
    private static final int MINIMUM_CLASS_VERSION = 51;

    protected ObfuscatingClassVisitor(final int api, final ClassVisitor classVisitor,
                                      final SymbolMapping symbolMapping, final Handle bootstrapMethodHandle) {
        super(api, classVisitor);

        this.symbolMapping         = Objects.requireNonNull(symbolMapping);
        this.bootstrapMethodHandle = Objects.requireNonNull(bootstrapMethodHandle);
    }

    @Override
    public void visit(int version, final int access, final String name, final String signature,
                      final String superName, final String[] interfaces) {
        version = Math.max(version, MINIMUM_CLASS_VERSION);
        super.visit(version, access, (className = name), signature, superName, interfaces);
    }

    protected String getClassName() {
        return className;
    }
}
