package dev.blanke.indyobfuscator.visitor;

/**
 * Denotes a 3-tuple which unique identifies a field in a program.
 */
public record FieldIdentifier(String owner, String name, String descriptor) {
}
