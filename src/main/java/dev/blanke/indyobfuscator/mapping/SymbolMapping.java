package dev.blanke.indyobfuscator.mapping;

/**
 * A {@code SymbolMapping} stores an identifier for each {@link MethodIdentifier} that is associated with a method to
 * be obfuscated. The identifier can be retrieved using the {@link #getName(MethodIdentifier)} method.
 */
@FunctionalInterface
public interface SymbolMapping {

    /**
     * Retrieves a unique name for the provided {@link MethodIdentifier} which will be passed to the
     * {@code invokedynamic} instruction.
     *
     * @param methodIdentifier Describes the method for which a unique name should be returned.
     *
     * @return A unique name for the provided {@code MethodIdentifier}.
     */
    String getName(MethodIdentifier methodIdentifier);

    /**
     * Denotes a 3-tuple which uniquely identifies a method in a program.
     *
     * @param owner The fully qualified name of the class defining the method. Note that slashes instead of spaces are
     *              used as the package separator.
     *
     * @param name The name of the method itself.
     *
     * @param descriptor A descriptor specifying the method signature.
     */
    record MethodIdentifier(String owner, String name, String descriptor) {}
}
