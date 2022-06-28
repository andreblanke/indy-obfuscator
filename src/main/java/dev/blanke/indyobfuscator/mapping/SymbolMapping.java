package dev.blanke.indyobfuscator.mapping;

import java.util.Map.Entry;

import dev.blanke.indyobfuscator.MethodIdentifier;

/**
 * A {@code SymbolMapping} stores an identifier for each {@link MethodIdentifier} that is associated with a method to
 * be obfuscated and allows iteration of these mappings.
 *
 * An individual identifier can be retrieved using the {@link #getName(MethodIdentifier)} method.
 */
public interface SymbolMapping extends Iterable<Entry<MethodIdentifier, Integer>> {

    /**
     * Retrieves a unique name for the provided {@link MethodIdentifier} which will be passed to the
     * {@code invokedynamic} instruction.
     *
     * @param methodIdentifier Describes the method for which a unique name should be returned.
     *
     * @return A unique name for the provided {@code MethodIdentifier}.
     */
    String getName(MethodIdentifier methodIdentifier);
}
