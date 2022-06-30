package dev.blanke.indyobfuscator.visitor.field;

import java.util.HashSet;
import java.util.Set;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import dev.blanke.indyobfuscator.visitor.field.FieldAccessorMethodVisitor.*;

import static org.objectweb.asm.Opcodes.*;

/**
 * A {@link ClassVisitor} which replaces eligible field instructions ({@link Opcodes#GETFIELD}, {@link Opcodes#PUTFIELD},
 * {@link Opcodes#GETSTATIC}, {@link Opcodes#PUTSTATIC}) with invocations to generated synthetic wrapper methods
 * containing the original field instruction.
 * <p>
 * The generated method invocation instructions which have replaced the original field instructions are included in
 * later obfuscation steps using the {@link dev.blanke.indyobfuscator.visitor.obfuscation.ObfuscatingClassVisitor},
 * basically extending the {@code invokedynamic} obfuscation technique to eligible fields.
 */
public final class FieldAccessWrappingClassVisitor extends ClassVisitor {

    /**
     * The name of the class currently being visited.
     * <p>
     * It is populated within {@link #visit(int, int, String, String, String, String[])}.
     */
    private String className;

    /**
     * The set of {@code final} fields declared by the class that is currently being visited.
     * <p>
     * It is populated within {@link #visitField(int, String, String, String, Object)}.
     * <p>
     * Field instructions with the opcodes {@link Opcodes#PUTFIELD} or {@link Opcodes#PUTSTATIC} are not obfuscated if
     * the field these instructions target is a member of this set. Obfuscating these field instructions would yield an
     * {@link IllegalAccessError} at runtime when an attempt of setting a {@code final} field is made from a context
     * that should not be able to do this (e.g. from a synthetic setter rather than from {@code <init>} or
     * {@code <clinit>}).
     *
     * @see #eligibleFieldAccesses
     */
    private final Set<FieldIdentifier> finalFields = new HashSet<>();

    /**
     * The set of {@link FieldAccess}es which are eligible for replacement with a method invocation to a synthetic
     * wrapper method ("wrappable").
     * <p>
     * A field access/field instruction is deemed to be eligible for replacement if it targets a non-{@code final} field
     * or if it targets a {@code final} field but is a {@link Opcodes#GETFIELD} or {@link Opcodes#GETSTATIC} instruction.
     *
     * @see #finalFields
     *
     * @see #visitMethod(int, String, String, String, String[])
     */
    private final Set<FieldAccess> eligibleFieldAccesses = new HashSet<>();

    public FieldAccessWrappingClassVisitor(final ClassVisitor classVisitor) {
        super(Opcodes.ASM9, classVisitor);
    }

    /**
     * Visits the header of the class in order to populate {@link #className}.
     */
    @Override
    public void visit(final int version, final int access, final String name, final String signature,
                      final String superName, final String[] interfaces) {
        super.visit(version, access, (className = name), signature, superName, interfaces);
    }

    /**
     * Visits a field of the class in order to populate the set of {@link #finalFields}.
     */
    @Override
    public FieldVisitor visitField(final int access, final String name, final String descriptor, final String signature,
                                   final Object value) {
        if ((access & ACC_FINAL) != 0) {
            finalFields.add(new FieldIdentifier(className, name, descriptor));
        }
        return super.visitField(access, name, descriptor, signature, value);
    }

    /**
     * Visits a method of the class, checking whether each field instruction is wrappable.
     * <p>
     * Wrappable field instructions are replaced with method invocations to their respective synthetic getters/setters
     * and added to the {@link #eligibleFieldAccesses} set while non-wrappable field instructions remain untouched.
     */
    @Override
    public MethodVisitor visitMethod(final int access, final String name, final String descriptor,
                                     final String signature, final String[] exceptions) {
        return new MethodVisitor(api, super.visitMethod(access, name, descriptor, signature, exceptions)) {
            @Override
            public void visitFieldInsn(final int opcode, final String owner, final String name,
                                       final String descriptor) {
                final var fieldIdentifier = new FieldIdentifier(owner, name, descriptor);
                if (((opcode == PUTFIELD) || (opcode == PUTSTATIC)) && finalFields.contains(fieldIdentifier)) {
                    /*
                     * Exclude PUT* instructions targeting a final field from obfuscation, as setting the final field
                     * from a different context (other than <init> and <clinit>) would cause an IllegalAccessError.
                     */
                    super.visitFieldInsn(opcode, owner, name, descriptor);
                    return;
                }

                /*
                 * Keep track of the field accesses to be obfuscated in order to generate the synthetic getters/setters
                 * within visitEnd.
                 */
                final var fieldAccess = new FieldAccess(className, opcode, fieldIdentifier);
                eligibleFieldAccesses.add(fieldAccess);

                // Replace the field instruction with a method invocation to the generated synthetic getter/setter.
                final var wrapper = fieldAccess.syntheticAccessorInvocation();
                super.visitMethodInsn(wrapper.opcode(), wrapper.owner(), wrapper.name(), wrapper.descriptor(), false);
            }
        };
    }

    /**
     * Visits the end of the class and generates synthetic wrapper methods for the eligible fields.
     */
    @Override
    public void visitEnd() {
        super.visitEnd();

        for (var fieldAccess : eligibleFieldAccesses) {
            final var wrapper = fieldAccess.syntheticAccessorInvocation();
            var methodVisitor =
                super.visitMethod(fieldAccess.syntheticAccessorInvocationAccess(), wrapper.name(), wrapper.descriptor(),
                    null, new String[0]);
            // Generate the correct method body depending on the opcode of the original field access.
            methodVisitor = switch (fieldAccess.opcode()) {
                case GETFIELD, GETSTATIC -> new FieldGetterMethodVisitor(api, methodVisitor, fieldAccess);
                case PUTFIELD, PUTSTATIC -> new FieldSetterMethodVisitor(api, methodVisitor, fieldAccess);
                default -> throw new IllegalArgumentException();
            };
            methodVisitor.visitCode();
            methodVisitor.visitMaxs(0, 0);
            methodVisitor.visitEnd();
        }
    }
}
