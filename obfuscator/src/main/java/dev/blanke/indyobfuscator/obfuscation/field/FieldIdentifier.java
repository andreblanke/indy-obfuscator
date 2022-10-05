package dev.blanke.indyobfuscator.obfuscation.field;

import static java.util.Objects.requireNonNull;

/**
 * Denotes a 3-tuple which uniquely identifies a field in a program.
 *
 * @param owner The internal name of the class defining the field.
 *
 * @param name The name of the field.
 *
 * @param descriptor A descriptor describing the type of the field.
 */
public record FieldIdentifier(String owner, String name, String descriptor) {

    public FieldIdentifier {
        requireNonNull(owner);
        requireNonNull(name);
        requireNonNull(descriptor);
    }
}
