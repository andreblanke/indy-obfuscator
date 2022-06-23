package dev.blanke.indyobfuscator.visitor;

import java.io.File;
import java.util.Objects;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import dev.blanke.indyobfuscator.BootstrapMethodConflictException;

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
     * @param api The ASM API version implemented by this visitor. Must be one of the {@code ASMx} values in
     *            {@link Opcodes}.
     *
     * @param classVisitor The {@link ClassVisitor} to which this visitor must delegate method calls.
     *                     May be {@code null}.
     *
     * @param bootstrapMethodHandle The {@link Handle} describing the bootstrap method which is intended to be created
     *                              inside the visited class. If a method with the same name and descriptor already
     *                              exists, a {@link BootstrapMethodConflictException} is thrown.
     */
    public BootstrappingClassVisitor(final int api, final ClassVisitor classVisitor,
                                     final Handle bootstrapMethodHandle) {
        super(api, classVisitor);

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

        final boolean isClinit = name.equals("<clinit>");
        visitedClinit |= isClinit;

        // Append library loading code for the bootstrap method if we are visiting <clinit>.
        final var methodVisitor = super.visitMethod(access, name, descriptor, signature, exceptions);
        return isClinit ? new ClinitMethodVisitor(api, methodVisitor) : methodVisitor;
    }

    @Override
    public void visitEnd() {
        // If <clinit> was not found, add a static initializer containing the library loading code.
        if (!visitedClinit) {
            final var clinitVisitor =
                new ClinitMethodVisitor(api, super.visitMethod(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null));
            clinitVisitor.visitCode();
            clinitVisitor.visitMaxs(-1, -1);
        }

        /*
         * Create the public, static, native, and synthetic bootstrap method. No additional post-processing needs to be
         * done to the returned MethodVisitor, as the created method is simply a stub on the Java side, due to being a
         * native method.
         */
        super.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_NATIVE | Opcodes.ACC_SYNTHETIC,
            bootstrapMethodHandle.getName(), bootstrapMethodHandle.getDesc(), null, null);
        super.visitEnd();
    }

    private static final class ClinitMethodVisitor extends MethodVisitor {

        ClinitMethodVisitor(final int api, final MethodVisitor methodVisitor) {
            super(api, methodVisitor);
        }

        @Override
        public void visitCode() {
            super.visitCode();

            // Create and initialize a StringBuilder.
            visitTypeInsn(Opcodes.NEW, Type.getInternalName(StringBuilder.class));
            visitInsn(Opcodes.DUP);
            visitMethodInsn(Opcodes.INVOKESPECIAL, Type.getInternalName(StringBuilder.class), "<init>", "()V", false);

            // Append current working directory retrieved using System.getProperty("user.dir") to the StringBuilder.
            visitLdcInsn("user.dir");
            visitMethodInsn(Opcodes.INVOKESTATIC, Type.getInternalName(System.class), "getProperty",
                "(Ljava/lang/String;)Ljava/lang/String;", false);
            visitMethodInsn(Opcodes.INVOKEVIRTUAL, Type.getInternalName(StringBuilder.class), "append",
                "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false);

            // Append the path separator to the StringBuilder.
            visitFieldInsn(Opcodes.GETSTATIC, Type.getInternalName(File.class), "separatorChar", "C");
            visitMethodInsn(Opcodes.INVOKEVIRTUAL, Type.getInternalName(StringBuilder.class), "append",
                "(C)Ljava/lang/StringBuilder;", false);

            // Append the platform-specific library name retrieved from System.mapLibraryName to the StringBuilder.
            visitLdcInsn("bootstrap");
            visitMethodInsn(Opcodes.INVOKESTATIC, Type.getInternalName(System.class), "mapLibraryName",
                "(Ljava/lang/String;)Ljava/lang/String;", false);
            visitMethodInsn(Opcodes.INVOKEVIRTUAL, Type.getInternalName(StringBuilder.class), "append",
                "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false);

            // Invoke the StringBuilder.toString() method and pass the result to System.load.
            visitMethodInsn(Opcodes.INVOKEVIRTUAL, Type.getInternalName(StringBuilder.class), "toString",
                "()Ljava/lang/String;", false);
            visitMethodInsn(Opcodes.INVOKESTATIC, Type.getInternalName(System.class), "load",
                "(Ljava/lang/String;)V", false);

            visitInsn(Opcodes.RETURN);
        }
    }
}
