package dev.blanke.indyobfuscator.obfuscation.bootstrap;

import java.io.File;
import java.util.Objects;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.commons.GeneratorAdapter;

import static org.objectweb.asm.Opcodes.*;
import static org.objectweb.asm.Type.CHAR_TYPE;
import static org.objectweb.asm.Type.getType;
import static org.objectweb.asm.commons.Method.getMethod;

/**
 * Denotes a {@link ClassVisitor} that adds the bootstrap method definition along with library loading code for its
 * native implementation to the class being visited.
 * <p>
 * The library loading code will be prepended to the class' static initializer {@code <clinit>}, creating the method
 * if it does not yet exist.
 */
public final class BootstrapMethodOwnerClassVisitor extends ClinitClassVisitor {

    /**
     * The handle containing information about the name and descriptor of the bootstrap method added by this
     * {@code ClassVisitor}.
     * <p>
     * A {@link BootstrapMethodConflictException} will be thrown if the visited class already contains a method with the
     * same name and descriptor.
     */
    private final Handle bootstrapMethodHandle;

    /**
     * Constructs a new {@link BootstrapMethodOwnerClassVisitor}.
     *
     * @param classVisitor The {@link ClassVisitor} to which this visitor must delegate method calls.
     *                     May be {@code null}.
     *
     * @param bootstrapMethodHandle The {@link Handle} describing the bootstrap method which is intended to be created
     *                              inside the visited class. If a method with the same name and descriptor already
     *                              exists, a {@link BootstrapMethodConflictException} is thrown.
     */
    public BootstrapMethodOwnerClassVisitor(final int api, final ClassVisitor classVisitor,
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
        return super.visitMethod(access, name, descriptor, signature, exceptions);
    }

    @Override
    public MethodVisitor visitClinit(final int access, final String name, final String descriptor,
                                     final String signature, final String[] exceptions,
                                     final MethodVisitor methodVisitor) {
        return new ClinitMethodVisitor(methodVisitor, access, name, descriptor);
    }

    @Override
    public void visitEnd() {
        /*
         * Create the public, static, native, and synthetic bootstrap method. No additional post-processing needs to be
         * done to the returned MethodVisitor, as the created method is simply a stub on the Java side, due to being a
         * native method.
         */
        super.visitMethod((ACC_PUBLIC | ACC_STATIC | ACC_NATIVE | ACC_SYNTHETIC),
            bootstrapMethodHandle.getName(), bootstrapMethodHandle.getDesc(), null, null);
        super.visitEnd();
    }

    private final class ClinitMethodVisitor extends GeneratorAdapter {

        private ClinitMethodVisitor(final MethodVisitor methodVisitor, final int access, final String name,
                                    final String descriptor) {
            super(BootstrapMethodOwnerClassVisitor.this.api, methodVisitor, access, name, descriptor);
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

        @Override
        public void visitMaxs(final int maxStack, final int maxLocals) {
            /*
             * The instructions added by visitCode do not use any local variables, so pass maxLocals through unmodified.
             *
             * At most two values are on the stack for the purpose of loading the bootstrap library: a StringBuilder
             * instance and a value to be appended to the StringBuilder, so raise maxStack to at least 2.
             */
            super.visitMaxs(Math.max(maxStack, 2), maxLocals);
        }
    }
}
