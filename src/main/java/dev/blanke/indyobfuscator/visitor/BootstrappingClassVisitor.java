package dev.blanke.indyobfuscator.visitor;

import java.io.File;
import java.util.Objects;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.commons.GeneratorAdapter;

import dev.blanke.indyobfuscator.BootstrapMethodConflictException;

import static org.objectweb.asm.Opcodes.*;
import static org.objectweb.asm.Type.CHAR_TYPE;
import static org.objectweb.asm.Type.getType;
import static org.objectweb.asm.commons.Method.getMethod;

public final class BootstrappingClassVisitor extends ClassVisitor {

    /**
     * Whether {@code <clinit>} has been visited inside of {@link #visitMethod(int, String, String, String, String[])},
     * meaning that a static initializer is already present inside the class we are visiting.
     *
     * If a static initializer already exists, the existing {@code <clinit>} needs to be modified to include the
     * library loading code for the implementation of the bootstrap method.
     *
     * Otherwise, if a static initializer does not yet exist, a new one containing just the library loading code is
     * created.
     *
     * @see #visitMethod(int, String, String, String, String[])
     * @see #visitEnd()
     */
    private boolean visitedClinit;

    private final Handle bootstrapMethodHandle;

    /**
     * Constructs a new {@link BootstrappingClassVisitor}.
     *
     * @param classVisitor The {@link ClassVisitor} to which this visitor must delegate method calls.
     *                     May be {@code null}.
     *
     * @param bootstrapMethodHandle The {@link Handle} describing the bootstrap method which is intended to be created
     *                              inside the visited class. If a method with the same name and descriptor already
     *                              exists, a {@link BootstrapMethodConflictException} is thrown.
     */
    public BootstrappingClassVisitor(final ClassVisitor classVisitor, final Handle bootstrapMethodHandle) {
        super(ASM9, classVisitor);

        this.bootstrapMethodHandle = Objects.requireNonNull(bootstrapMethodHandle);
    }

    @Override
    public MethodVisitor visitMethod(final int access, final String name, final String descriptor,
                                     final String signature, final String[] exceptions) {
        /*
         * Check if we are currently visiting a method with the same name as the bootstrap method which we want to add
         * to the class, in which case we throw an exception and inform the user to choose a different name for the
         * bootstrap method.
         */
        if (name.equals(bootstrapMethodHandle.getName()) && descriptor.equals(bootstrapMethodHandle.getDesc())) {
            throw new BootstrapMethodConflictException();
        }

        // Append library loading code for the bootstrap method if we are visiting <clinit>.
        final var methodVisitor = super.visitMethod(access, name, descriptor, signature, exceptions);
        if (!(visitedClinit |= name.equals("<clinit>")))
            return methodVisitor;
        return new ClinitMethodVisitor(api, methodVisitor, access, name, descriptor);
    }

    @Override
    public void visitEnd() {
        // If <clinit> was not found, add a static initializer containing the library loading code.
        if (!visitedClinit) {
            final var clinitVisitor = new ClinitMethodVisitor(api, super.visitMethod(
                ACC_STATIC, "<clinit>", "()V", null, null),
                ACC_STATIC, "<clinit>", "()V");
            clinitVisitor.visitCode();
            clinitVisitor.visitInsn(RETURN);
            clinitVisitor.visitMaxs(0, 0); // Dummy values, actual values will be computed automatically.
            clinitVisitor.visitEnd();
        }

        /*
         * Create the public, static, native, and synthetic bootstrap method. No additional post-processing needs to be
         * done to the returned MethodVisitor, as the created method is simply a stub on the Java side, due to being a
         * native method.
         */
        super.visitMethod(ACC_PUBLIC | ACC_STATIC | ACC_NATIVE | ACC_SYNTHETIC,
            bootstrapMethodHandle.getName(), bootstrapMethodHandle.getDesc(), null, null);
        super.visitEnd();
    }

    private static final class ClinitMethodVisitor extends GeneratorAdapter {

        ClinitMethodVisitor(final int api, final MethodVisitor methodVisitor, final int access, final String name,
                            final String descriptor) {
            super(api, methodVisitor, access, name, descriptor);
        }

        @Override
        public void visitCode() {
            super.visitCode();

            // Create and initialize a StringBuilder.
            newInstance(getType(StringBuilder.class));
            dup();
            invokeConstructor(getType(StringBuilder.class), getMethod("void <init>()"));

            // Append current working directory retrieved using System.getProperty("user.dir") to the StringBuilder.
            visitLdcInsn("user.dir");
            invokeStatic(getType(System.class),         getMethod("java.lang.String getProperty(java.lang.String)"));
            invokeVirtual(getType(StringBuilder.class), getMethod("java.lang.StringBuilder append(java.lang.String)"));

            // Append the path separator to the StringBuilder.
            getStatic(getType(File.class), "separatorChar", CHAR_TYPE);
            invokeVirtual(getType(StringBuilder.class), getMethod("java.lang.StringBuilder append (char)"));

            // Append the platform-specific library name retrieved from System.mapLibraryName to the StringBuilder.
            visitLdcInsn("bootstrap"); // Name of the library to load. TODO: Consider making this configurable.
            invokeStatic(getType(System.class),         getMethod("java.lang.String mapLibraryName(java.lang.String)"));
            invokeVirtual(getType(StringBuilder.class), getMethod("java.lang.StringBuilder append(java.lang.String)"));

            // Invoke the StringBuilder.toString() method and pass the result to System.load.
            invokeVirtual(getType(StringBuilder.class), getMethod("java.lang.String toString()"));
            invokeStatic(getType(System.class),         getMethod("void load(java.lang.String)"));
        }
    }
}
