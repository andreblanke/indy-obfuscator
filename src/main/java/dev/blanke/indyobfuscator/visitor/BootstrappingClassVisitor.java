package dev.blanke.indyobfuscator.visitor;

import java.util.Objects;
import java.util.UUID;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

public final class BootstrappingClassVisitor extends ClassVisitor {

    /**
     * Whether a method with the same name and signature as the bootstrap method has been visited inside of
     * {@link #visitMethod(int, String, String, String, String[])}, meaning that a conflicting method is already
     * present inside the class we are visiting.
     *
     * If a conflicting method already exists, the {@link #bootstrapMethodHandle} is replaced by a {@link Handle}
     * with a different name to resolve the conflict. For further processing, {@link ObfuscatingClassVisitor}s need to
     * be passed the non-conflicting {@code Handle} which can be retrieved using {@link #getBootstrapMethodHandle()}.
     *
     * @see #visitMethod(int, String, String, String, String[])
     * @see #visitEnd()
     * @see #getBootstrapMethodHandle()
     */
    private boolean visitedBootstrapMethod;

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

    private Handle bootstrapMethodHandle;

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
     *                              exists, the name of the {@code Handle} is ignored and a different one retrievable
     *                              via {@link #getBootstrapMethodHandle()} is used.
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
         * to the class, in which case we have to rename our bootstrap method.
         */
        visitedBootstrapMethod |=
            (name.equals(bootstrapMethodHandle.getName()) && descriptor.equals(bootstrapMethodHandle.getDesc()));

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
        }

        /*
         * Resolve conflicting method names by appending a random UUID to the originally intended name of the bootstrap
         * method. The use of a random UUID makes another conflict highly unlikely and produces a legal name according
         * to JVMS §4.3.4.
         */
        if (visitedBootstrapMethod) {
            bootstrapMethodHandle = new Handle(
                bootstrapMethodHandle.getTag(),
                bootstrapMethodHandle.getOwner(),
                bootstrapMethodHandle.getName() + UUID.randomUUID(),
                bootstrapMethodHandle.getDesc(),
                bootstrapMethodHandle.isInterface());
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

    public Handle getBootstrapMethodHandle() {
        return bootstrapMethodHandle;
    }

    private static final class ClinitMethodVisitor extends MethodVisitor {

        ClinitMethodVisitor(final int api, final MethodVisitor methodVisitor) {
            super(api, methodVisitor);
        }

        @Override
        public void visitCode() {
            super.visitCode();

            // TODO
            visitLdcInsn("test");
            visitMethodInsn(
                Opcodes.INVOKESTATIC, Type.getDescriptor(System.class), "load", "(Ljava/lang/String;)V", false);
        }
    }
}
