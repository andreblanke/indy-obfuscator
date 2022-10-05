package dev.blanke.indyobfuscator;

import java.io.FileWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.instrument.Instrumentation;
import java.util.Properties;

import org.jetbrains.annotations.Nullable;

public final class DynamicAnalysisAgent {

    public static void premain(final @Nullable String agentArgs, final Instrumentation  instrumentation) {
        arguments = new Arguments(agentArgs);

        instrumentation.addTransformer(new ClassFileTransformer(
            arguments.getObfuscationBootstrapMethodHandle(), arguments.getDecoratorBootstrapMethodHandle()));

        Runtime.getRuntime().addShutdownHook(new Thread(DynamicAnalysisAgent::dumpSymbolTable));
    }

    private static void dumpSymbolTable() {
        try {
            final var properties = new Properties();
            properties.putAll(BootstrapDecorator.getSymbolTable());
            properties.store(new FileWriter("mappings.properties"), null);
        } catch (final IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static Arguments arguments;
    static Arguments getArguments() {
        return arguments;
    }
}
