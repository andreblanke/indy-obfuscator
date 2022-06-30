package dev.blanke.indyobfuscator.visitor;

import java.util.Objects;
import java.util.UUID;

import dev.blanke.indyobfuscator.MethodIdentifier;

import static org.objectweb.asm.Opcodes.*;

public final class FieldAccessorIdentifier extends MethodIdentifier {

    private final int fieldOpcode;

    private final String fieldOwner;

    private final String fieldName;

    private final String fieldDescriptor;

    public FieldAccessorIdentifier(final String owner, final int fieldOpcode, final String fieldOwner,
                                   final String fieldName, final String fieldDescriptor) {
        /*
         * Always generate the synthetic accessor as static method for simplicity, as the field being accessed might
         * not necessarily be located in the class containing the field instruction that will be replaced by a method
         * invocation.
         */
        super(INVOKESTATIC, owner,
            deriveMethodName(fieldName), deriveMethodDescriptor(fieldOpcode, fieldOwner, fieldDescriptor));

        this.fieldOpcode     = fieldOpcode;
        this.fieldOwner      = Objects.requireNonNull(fieldOwner);
        this.fieldName       = Objects.requireNonNull(fieldName);
        this.fieldDescriptor = Objects.requireNonNull(fieldDescriptor);
    }

    private static String deriveMethodName(final String fieldName) {
        /*
         * The possibility of collision with an existing method inside the owner would be too high if the original
         * field name was used as-is, so append a random part to the original field name to make it unique.
         *
         * Prepending the original field name is required, as it is guaranteed to be a valid Java identifier
         * (assuming the input bytecode is valid) - a condition not necessarily met by a randomly generated UUID.
         *
         * Including the name of the field does not weaken the obfuscation effort, as the method body will contain
         * instructions to get/put the field which leak that information anyway.
         */
        return fieldName + UUID.randomUUID().toString().replaceAll("-", "");
    }

    private static String deriveMethodDescriptor(final int    fieldOpcode,
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
     * @see #getOpcode()
     */
    public int getAccess() {
        return ACC_PRIVATE | ACC_STATIC | ACC_SYNTHETIC;
    }

    public int getFieldOpcode() {
        return fieldOpcode;
    }

    public String getFieldOwner() {
        return fieldOwner;
    }

    public String getFieldName() {
        return fieldName;
    }

    public String getFieldDescriptor() {
        return fieldDescriptor;
    }
}
