package dev.blanke.indyobfuscator.mapping;

import java.util.Map.Entry;

/**
 * A {@code SymbolMapping} stores a unique identifier for each {@link MethodInvocation} that will be obfuscated.
 * <p>
 * {@code MethodInvocation}s can be included in a {@code SymbolMapping} via {@link #add(MethodInvocation)},
 * which will cause the generation of an implementation-dependent, unique identifier.
 */
public interface SymbolMapping extends Iterable<Entry<MethodInvocation, Integer>> {

    /**
     * Computes a unique name for the provided {@link MethodInvocation} and stores the association in this
     * {@code SymbolMapping}.
     *
     * @param methodInvocation Describes the method invocation for which a unique name should be returned.
     *
     * @return A unique name for the provided {@code MethodIdentifier}.
     */
    String add(MethodInvocation methodInvocation);
}
