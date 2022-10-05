package dev.blanke.indyobfuscator.mapping;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicInteger;

import org.jetbrains.annotations.NotNull;

/**
 * A {@link SymbolMapping} implementation which assigns a unique, sequentially-increasing number for each encountered
 * {@link MethodInvocation}.
 * <p>
 * Because the iteration order of class files within a jar file and the iteration order of method instructions within
 * each class might be stable (depending on the {@link java.nio.file.FileSystem} implementation and the implementation
 * details of the ASM library), the obfuscation might be weaker when this {@code SymbolMapping} implementation is used,
 * as the generated mapping could be deterministic.
 */
public final class SequentialSymbolMapping implements SymbolMapping {

    private final AtomicInteger counter = new AtomicInteger();

    private final Map<MethodInvocation, Integer> symbolMapping = new HashMap<>();

    @NotNull
    @Override
    public Iterator<Entry<MethodInvocation, Integer>> iterator() {
        return symbolMapping.entrySet().iterator();
    }

    @Override
    public String add(final MethodInvocation methodInvocation) {
        return symbolMapping.computeIfAbsent(methodInvocation, key -> counter.getAndIncrement()).toString();
    }
}
