package dev.blanke.indyobfuscator.mapping;

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
public record MethodIdentifier(String owner, String name, String descriptor, int opcode) {}
