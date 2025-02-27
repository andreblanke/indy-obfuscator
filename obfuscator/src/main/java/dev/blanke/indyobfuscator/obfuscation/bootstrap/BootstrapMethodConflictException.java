package dev.blanke.indyobfuscator.obfuscation.bootstrap;

/**
 * A {@code BootstrapMethodConflictException} is thrown if a method with the same name and descriptor as the bootstrap
 * method to be added already exists in the class which should own the bootstrap method.
 *
 * @implNote Cannot be a checked {@link Exception}, as {@link org.objectweb.asm.ClassVisitor}s must be able to throw
 *           this exception from overridden methods.
 *
 * @see BootstrapMethodOwnerClassVisitor
 */
public final class BootstrapMethodConflictException extends RuntimeException {
}
