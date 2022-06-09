package dev.blanke.indyobfuscator.mapping;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A {@link SymbolMapping} implementation which assigns a unique number for each encountered {@link MethodIdentifier}.
 *
 * Because the iteration order of class files within a .jar file and the iteration order of methods within each class
 * might be stable, the obfuscation might be weaker when this {@code SymbolMapping} implementation is used,
 * as the mapping could be deterministic.
 */
public final class SequentialSymbolMapping implements SymbolMapping {

    private final AtomicInteger counter = new AtomicInteger();

    private final Map<MethodIdentifier, Integer> symbolMapping = new HashMap<>();

    @Override
    public String getName(final MethodIdentifier methodIdentifier) {
        return symbolMapping.computeIfAbsent(methodIdentifier, key -> counter.getAndIncrement()).toString();
    }
}
