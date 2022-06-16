package dev.blanke.indyobfuscator;

/**
 * @implNote Cannot be a checked {@link Exception}, as {@link org.objectweb.asm.ClassVisitor}s must be able to throw
 *           this exception from overridden methods.
 */
public final class BootstrapMethodConflictException extends RuntimeException {
}
