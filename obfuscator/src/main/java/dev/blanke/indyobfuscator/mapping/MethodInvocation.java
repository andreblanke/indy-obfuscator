package dev.blanke.indyobfuscator.mapping;

import java.util.Objects;

import org.objectweb.asm.Opcodes;

/**
 * Denotes a 4-tuple which uniquely identifies an invocation of a specific method in a program.
 * <p>
 * Instances of this class are passed to templates as part of the generated {@link SymbolMapping} to facilitate the
 * generation of a bootstrap method implementation.
 *
 * @param opcode The opcode that is used to invoke the method. See {@link org.objectweb.asm.Opcodes}.
 *
 * @param owner The internal name of the class defining the invoked method.
 *
 * @param name The name of the method being invoked.
 *
 * @param descriptor A descriptor specifying the parameter and return types of the invoked method as a string.
 *
 * @param caller The internal name of the class containing the method invocation. Only relevant when {@link #opcode} is
 *               {@link Opcodes#INVOKESPECIAL}.
 */
public record MethodInvocation(int opcode, String owner, String name, String descriptor, String caller) {

    public MethodInvocation {
        Objects.requireNonNull(owner);
        Objects.requireNonNull(name);
        Objects.requireNonNull(descriptor);
    }

    @Override
    public boolean equals(final Object object) {
        if (!(object instanceof MethodInvocation other))
            return false;
        return (opcode() == other.opcode()
            && Objects.equals(owner(),      other.owner())
            && Objects.equals(name(),       other.name())
            && Objects.equals(descriptor(), other.descriptor())
            && ((opcode() != Opcodes.INVOKESPECIAL) || Objects.equals(caller(), other.caller())));
    }

    @Override
    public int hashCode() {
        var hashCode = Objects.hash(opcode(), owner(), name(), descriptor());
        if (opcode() == Opcodes.INVOKESPECIAL) {
            hashCode = (31 * hashCode + Objects.hashCode(caller()));
        }
        return hashCode;
    }
}
