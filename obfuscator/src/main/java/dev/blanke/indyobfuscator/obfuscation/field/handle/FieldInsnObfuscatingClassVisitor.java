package dev.blanke.indyobfuscator.obfuscation.field.handle;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.HashSet;
import java.util.Set;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import dev.blanke.indyobfuscator.mapping.MethodInvocation;
import dev.blanke.indyobfuscator.mapping.SymbolMapping;
import dev.blanke.indyobfuscator.obfuscation.ObfuscatingClassVisitor;
import dev.blanke.indyobfuscator.obfuscation.field.FieldIdentifier;

import static org.objectweb.asm.Opcodes.*;
import static org.objectweb.asm.Opcodes.PUTSTATIC;
import static org.objectweb.asm.Type.getMethodType;
import static org.objectweb.asm.Type.getObjectType;
import static org.objectweb.asm.Type.getType;

public final class FieldInsnObfuscatingClassVisitor extends ObfuscatingClassVisitor {

    private final Set<FieldIdentifier> finalFields = new HashSet<>();

    private static final Logger LOGGER = System.getLogger(FieldInsnObfuscatingClassVisitor.class.getName());

    public FieldInsnObfuscatingClassVisitor(final int api, final ClassVisitor classVisitor,
                                            final SymbolMapping symbolMapping, final Handle bootstrapMethodHandle) {
        super(api, classVisitor, symbolMapping, bootstrapMethodHandle);
    }

    @Override
    public FieldVisitor visitField(final int access, final String name, final String descriptor, final String signature,
                                   final Object value) {
        if ((access & Opcodes.ACC_FINAL) != 0)
            finalFields.add(new FieldIdentifier(getClassName(), name, descriptor));
        return super.visitField(access, name, descriptor, signature, value);
    }

    @Override
    public MethodVisitor visitMethod(final int access, final String name, final String descriptor,
                                     final String signature, final String[] exceptions) {
        return new FieldInsnObfuscatingMethodVisitor(super.visitMethod(access, name, descriptor, signature, exceptions));
    }

    private final class FieldInsnObfuscatingMethodVisitor extends MethodVisitor {

        private FieldInsnObfuscatingMethodVisitor(final MethodVisitor methodVisitor) {
            super(FieldInsnObfuscatingClassVisitor.this.api, methodVisitor);
        }

        @Override
        public void visitFieldInsn(final int opcode, final String owner, final String name, final String descriptor) {
            final var fieldIdentifier = new FieldIdentifier(owner, name, descriptor);
            if (((opcode == PUTFIELD) || (opcode == PUTSTATIC)) && finalFields.contains(fieldIdentifier)) {
                LOGGER.log(Level.INFO, "Skipping obfuscation of put on final field {0}.{1}.", owner, name);
                super.visitFieldInsn(opcode, owner, name, descriptor);
                return;
            }

            final var descriptorType = getType(descriptor);
            final var ownerType      = getObjectType(owner);

            final Type invokeDynamicDescriptorType =
                switch (opcode) {
                    case GETFIELD  -> getMethodType(descriptorType, ownerType);
                    case PUTFIELD  -> getMethodType(getType(void.class), ownerType, descriptorType);
                    case GETSTATIC -> getMethodType(descriptorType);
                    case PUTSTATIC -> getMethodType(getType(void.class), descriptorType);
                    default -> throw new IllegalArgumentException();
                };

            final var invokeDynamicName =
                symbolMapping.add(new MethodInvocation(opcode, owner, name, descriptor, getClassName()));
            super.visitInvokeDynamicInsn(
                invokeDynamicName, invokeDynamicDescriptorType.getDescriptor(), bootstrapMethodHandle);
        }
    }
}
