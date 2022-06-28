package dev.blanke.indyobfuscator.visitor;

import java.util.UUID;

import dev.blanke.indyobfuscator.MethodIdentifier;

import static org.objectweb.asm.Opcodes.*;

/**
 * @implNote {@link FieldAccessorIdentifier#opcode}, {@link FieldAccessorIdentifier#name}, and
 *           {@link FieldAccessorIdentifier#descriptor} inherited from {@link MethodIdentifier} are repurposed to
 *           hold the information of the original field.
 *           The public {@code get*} methods are used to compute the true opcode, name, and descriptor of the method
 *           based on the values of the original field.
 *           Additional {@code getField*} methods have been introduced for more explicitness.
 */
public final class FieldAccessorIdentifier extends MethodIdentifier {

    public FieldAccessorIdentifier(final int fieldOpcode, final String fieldOwner, final String fieldName,
                                   final String fieldDescriptor) {
        super(fieldOpcode, fieldOwner, fieldName, fieldDescriptor);
    }

    @Override
    public int getOpcode() {
        return switch (getFieldOpcode()) {
            case GETFIELD,  PUTFIELD  -> INVOKEVIRTUAL;
            case GETSTATIC, PUTSTATIC -> INVOKESTATIC;
            default -> throw new IllegalArgumentException("Unrecognized field opcode '%d'".formatted(opcode));
        };
    }

    @Override
    public String getName() {
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
        return getFieldName() + UUID.randomUUID().toString().replaceAll("-", "");
    }

    @Override
    public String getDescriptor() {
        return switch (getFieldOpcode()) {
            case GETFIELD, GETSTATIC -> "()" + getFieldDescriptor();
            case PUTFIELD, PUTSTATIC -> "("  + getFieldDescriptor() + ")V";
            default -> throw new IllegalArgumentException("Unrecognized field opcode '%d'".formatted(opcode));
        };
    }

    public int getAccess() {
        int access = ACC_PRIVATE | ACC_SYNTHETIC;
        if ((getFieldOpcode() == GETSTATIC) || (getFieldOpcode() == PUTSTATIC))
            access |= ACC_STATIC;
        return access;
    }

    public int getFieldOpcode() {
        return opcode;
    }

    public String getFieldOwner() {
        return owner;
    }

    public String getFieldName() {
        return name;
    }

    public String getFieldDescriptor() {
        return descriptor;
    }
}
