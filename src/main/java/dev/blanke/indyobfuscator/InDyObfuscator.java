package dev.blanke.indyobfuscator;

import java.io.File;
import java.io.FileInputStream;
import java.io.PrintWriter;
import java.lang.invoke.CallSite;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.file.Files;
import java.util.concurrent.Callable;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.util.CheckClassAdapter;

import picocli.CommandLine;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import dev.blanke.indyobfuscator.mapping.SequentialSymbolMapping;
import dev.blanke.indyobfuscator.mapping.SymbolMapping;

public final class InDyObfuscator implements Callable<Integer> {

    @Parameters(
        index       = "0",
        description = "The .jar or .class file to be obfuscated.")
    private File input;

    @Option(
        names       = { "-o", "--output" },
        description = "Write obfuscated content to file instead of manipulating input in place")
    private File output;

    // TODO: Properly implement SymbolMapping
    private final SymbolMapping symbolMapping = new SequentialSymbolMapping();

    /**
     * The method descriptor specifying the signature of the used bootstrap method.
     *
     * @see #BOOTSTRAP_METHOD_HANDLE
     */
    private static final String BOOTSTRAP_METHOD_DESCRIPTOR =
        "(" + Type.getDescriptor(MethodHandles.Lookup.class)
            + Type.getDescriptor(String.class)     // invokedName
            + Type.getDescriptor(MethodType.class) // invokedType
            +
        ")" + Type.getDescriptor(CallSite.class);

    private static final Handle BOOTSTRAP_METHOD_HANDLE =
        new Handle(Opcodes.H_INVOKESTATIC, /* TODO */ "", /* TODO */ "", BOOTSTRAP_METHOD_DESCRIPTOR, false);

    public static void main(final String... args) {
        final int exitCode = new CommandLine(new InDyObfuscator()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() throws Exception {
        final var reader          = new ClassReader(new FileInputStream(input));
        final var obfuscatedClass = obfuscate(reader, BOOTSTRAP_METHOD_HANDLE);

        final byte[] obfuscatedClassBytes = obfuscatedClass.toByteArray();
        CheckClassAdapter.verify(new ClassReader(obfuscatedClassBytes), false, new PrintWriter(System.err));
        Files.write(getOutput().toPath(), obfuscatedClassBytes);
        return 0;
    }

    private ClassWriter obfuscate(final ClassReader reader, final Handle bootstrapMethodHandle) {
        final var writer = new ClassWriter(reader, 0);
        // Expanded frames are required for LocalVariablesSorter.
        reader.accept(new ObfuscatingClassVisitor(Opcodes.ASM9, writer, symbolMapping, bootstrapMethodHandle),
            ClassReader.EXPAND_FRAMES);
        return writer;
    }

    /**
     * Returns the {@link File} to which the obfuscated output should be written.
     *
     * @return {@link #output} if the option was given on the command line, otherwise {@link #input} for in-place
     *         obfuscation.
     */
    public File getOutput() {
        return (output != null) ? output : input;
    }
}
