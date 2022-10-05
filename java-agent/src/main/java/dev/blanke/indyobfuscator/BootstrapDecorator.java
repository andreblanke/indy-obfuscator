package dev.blanke.indyobfuscator;

import java.lang.invoke.CallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

public final class BootstrapDecorator {

    private static volatile Field memberField;

    @SuppressWarnings("unused")
    public static CallSite decoratedBootstrap(final MethodHandles.Lookup lookup, final String invokedName,
                                              final MethodType invokedType) throws Exception {
        final var callSite = (CallSite) DynamicAnalysisAgent.getArguments()
            .getObfuscationBootstrapMethod().invoke(null, lookup, invokedName, invokedType);

        // Assumes the MethodHandle is an instance of DirectMethodHandle as returned by MethodHandles.Lookup.
        final MethodHandle target = callSite.getTarget();
        if (memberField == null) {
            synchronized (BootstrapDecorator.class) {
                if (memberField == null) {
                    // Requires the "--add-opens java.base/java.lang.invoke=ALL-UNNAMED" JVM argument.
                    memberField = target.getClass().getDeclaredField("member");
                    memberField.setAccessible(true);
                }
            }
        }
        symbolTable.put(invokedName, memberField.get(target).toString());
        return callSite;
    }

    private static final Map<String, String> symbolTable = new HashMap<>();
    static Map<String, String> getSymbolTable() {
        return symbolTable;
    }
}
