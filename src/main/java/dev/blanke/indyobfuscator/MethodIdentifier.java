package dev.blanke.indyobfuscator;

import java.util.Objects;

/**
 * Denotes a 4-tuple which uniquely identifies a method in a program. Instances of this class are passed to templates
 * as part of the generated {@link dev.blanke.indyobfuscator.mapping.SymbolMapping} to facilitate the generation of a
 * bootstrap method implementation.
 */
public class MethodIdentifier {

    /**
     * The opcode that needs to be used to invoke the method described by this {@code MethodIdentifier}.
     */
    protected final int opcode;

    /**
     * The fully qualified name of the class defining the method. Note that slashes instead of dots are used as the
     * package separator.
     */
    protected final String owner;

    /**
     * The name of the method itself.
     */
    protected final String name;

    /**
     * A descriptor specifying the method signature.
     */
    protected final String descriptor;

    public MethodIdentifier(final int opcode, final String owner, final String name, final String descriptor) {
        this.opcode     = opcode;
        this.owner      = owner;
        this.name       = name;
        this.descriptor = descriptor;
    }

    @Override
    public boolean equals(final Object object) {
        return (this == object) || (object instanceof MethodIdentifier other)
            && (opcode == other.opcode)
            && Objects.equals(owner,      other.owner)
            && Objects.equals(name,       other.name)
            && Objects.equals(descriptor, other.descriptor);
    }

    @Override
    public int hashCode() {
        return Objects.hash(opcode, owner, name, descriptor);
    }

    public int getOpcode() {
        return opcode;
    }

    public String getOwner() {
        return owner;
    }

    public String getName() {
        return name;
    }

    public String getDescriptor() {
        return descriptor;
    }
}
