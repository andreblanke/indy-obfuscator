package dev.blanke.indyobfuscator.mapping;

import static java.util.Objects.requireNonNull;

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
 */
public record MethodInvocation(int opcode, String owner, String name, String descriptor) {

    public MethodInvocation {
        requireNonNull(owner);
        requireNonNull(name);
        requireNonNull(descriptor);
    }
}
