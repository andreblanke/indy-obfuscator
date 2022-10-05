package dev.blanke.indyobfuscator.obfuscation.field.accessor;

import java.util.UUID;

import dev.blanke.indyobfuscator.mapping.MethodInvocation;
import dev.blanke.indyobfuscator.obfuscation.field.FieldIdentifier;

import static java.util.Objects.requireNonNull;

import static org.objectweb.asm.Opcodes.*;

/**
 * Represents a field instruction/field access within a method body.
 *
 * @param syntheticAccessorInvocation A {@link MethodInvocation} to a synthetic getter/setter which can replace the
 *                                    original field access.
 *
 * @param opcode The type of field access being made. See {@link org.objectweb.asm.Opcodes}.
 *
 * @param fieldIdentifier The identifier for the field being targeted by the field access.
 */
record FieldAccess(MethodInvocation syntheticAccessorInvocation, int opcode, FieldIdentifier fieldIdentifier) {

    FieldAccess {
        requireNonNull(syntheticAccessorInvocation);
        requireNonNull(fieldIdentifier);
    }

    /**
     * Creates a new {@code FieldAccess} instance whose associated {@link MethodInvocation} is derived from the provided
     * {@code syntheticAccessorOwner}, {@code opcode}, and {@code fieldIdentifier}.
     *
     * @param syntheticAccessorOwner The internal name of the class which will define the synthetic accessor method.
     *
     * @param opcode The type of field access being made. See {@link org.objectweb.asm.Opcodes}.
     *               Required for the derivation of the correct {@link MethodInvocation#descriptor()}.
     *
     * @param fieldIdentifier The identifier for the field being targeted by the field access.
     *                        Required for the derivation of the correct {@link MethodInvocation#name()} and
     *                        {@link MethodInvocation#descriptor()}.
     */
    FieldAccess(final String syntheticAccessorOwner, final int opcode, final FieldIdentifier fieldIdentifier) {
        /*
         * The class accessing the field might not necessarily be the class declaring the field. This means that a
         * non-static synthetic accessor (invoked using INVOKEVIRTUAL) cannot be used in the general case but rather
         * only if the declaring class and the accessing class of the field match.
         */
        this(new MethodInvocation(INVOKESTATIC, syntheticAccessorOwner,
                deriveSyntheticAccessorName(fieldIdentifier.name()),
                deriveSyntheticAccessorDescriptor(opcode, fieldIdentifier.owner(), fieldIdentifier.descriptor()), null),
            opcode, fieldIdentifier);
    }

    /**
     * Derives a unique name for a synthetic accessor method from the provided {@code fieldName}.
     * <p>
     * Using the original {@code fieldName} directly as name for the synthetic accessor method would yield a high
     * possibility of collisions with existing methods inside the owner, so a random part is appended to the original
     * {@code fieldName} to guarantee uniqueness.
     * <p>
     * Including the name of the field does not weaken the obfuscation effort, as the method body will contain
     * instructions to get/put the field which leak the information anyway.
     *
     * @param fieldName The name of the field for which a synthetic accessor method name is to be computed.
     *
     * @return A unique name for a synthetic accessor method derived from the provided {@code fieldName}.
     */
    private static String deriveSyntheticAccessorName(final String fieldName) {
        /*
         * Prepending the original field name should guarantee that the output is a valid Java identifier (assuming the
         * input bytecode is valid) - a condition not necessarily met if solely a randomly generated UUID was used.
         */
        return fieldName + UUID.randomUUID().toString().replaceAll("-", "");
    }

    /**
     * Derives a descriptor for a synthetic accessor method from the provided {@code fieldOpcode}, {@code fieldOwner},
     * and {@code fieldDescriptor}.
     *
     * @param fieldOpcode The type of field access being made.
     *
     * @param fieldOwner The internal name of the class defining the field.
     *
     * @param fieldDescriptor A descriptor describing the type of the field.
     *
     * @return A descriptor containing information about the parameter and return types of a synthetic accessor method
     *         fitting for the given parameters.
     */
    private static String deriveSyntheticAccessorDescriptor(final int    fieldOpcode,
                                                            final String fieldOwner,
                                                            final String fieldDescriptor) {
        final var ownerDescriptor = 'L' + fieldOwner + ';';
        return switch (fieldOpcode) {
            case GETFIELD  -> "("  + ownerDescriptor + ")" + fieldDescriptor;
            case PUTFIELD  -> "("  + ownerDescriptor + fieldDescriptor + ")V";
            case GETSTATIC -> "()" + fieldDescriptor;
            case PUTSTATIC -> "("  + fieldDescriptor + ")V";
            default -> throw new IllegalArgumentException("Unrecognized field opcode '%d'".formatted(fieldOpcode));
        };
    }

    /**
     * Returns the access flags to use during the generation of the synthetic accessor method whose invocation will
     * replace this {@code FieldAccess}.
     *
     * @return The access flags to use for the synthetic accessor method.
     */
    public int syntheticAccessorInvocationAccess() {
        /*
         * See the constructor for an explanation on why ACC_STATIC is used even if the field access targets a
         * non-static field.
         */
        return ACC_PRIVATE | ACC_STATIC | ACC_SYNTHETIC;
    }
}
