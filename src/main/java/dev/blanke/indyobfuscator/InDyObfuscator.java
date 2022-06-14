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
import dev.blanke.indyobfuscator.visitor.BootstrappingClassVisitor;
import dev.blanke.indyobfuscator.visitor.ObfuscatingClassVisitor;

public final class InDyObfuscator implements Callable<Integer> {

    @Parameters(
        index       = "0",
        description = "The .jar or .class file to be obfuscated.")
    private File input;

    @Option(
        names       = { "-o", "--output" },
        description = "Write obfuscated content to file instead of manipulating input in place")
    private File output;

    /**
     * A reference to the bootstrap method to which {@code invokedynamic} instructions delegate.
     *
     * The owner of the bootstrap method depends on the obfuscation taking place: if a class file is being obfuscated,
     * the contained class is also the owner of the bootstrap method. In case a jar file is being obfuscated,
     * the main class will be the owner.
     *
     * @see #BOOTSTRAP_METHOD_DESCRIPTOR
     */
    private Handle bootstrapMethodHandle;

    private final SymbolMapping symbolMapping = new SequentialSymbolMapping();

    /**
     * @implNote Make sure to update the test source code when changing this value.
     */
    static final String BOOTSTRAP_METHOD_DEFAULT_NAME = "bootstrap";

    /**
     * The method descriptor specifying the signature of the used bootstrap method.
     *
     * @see #bootstrapMethodHandle
     */
    static final String BOOTSTRAP_METHOD_DESCRIPTOR =
        "(" + Type.getDescriptor(MethodHandles.Lookup.class)
            + Type.getDescriptor(String.class)     // invokedName
            + Type.getDescriptor(MethodType.class) // invokedType
            +
        ")" + Type.getDescriptor(CallSite.class);

    public static void main(final String... args) {
        final int exitCode = new CommandLine(new InDyObfuscator()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() throws Exception {
        final var reader = new ClassReader(new FileInputStream(input));
        final var writer = new ClassWriter(reader, 0);

        /*
         * TODO: Use main class name in case of jar file obfuscation.
         * TODO: Only add bootstrap method if we are processing the main class of a jar file.
         */
        bootstrapMethodHandle = new Handle(Opcodes.H_INVOKESTATIC, reader.getClassName(), BOOTSTRAP_METHOD_DEFAULT_NAME,
            BOOTSTRAP_METHOD_DESCRIPTOR, false);
        addBootstrapMethod(reader, writer);

        obfuscate(reader, writer);

        final byte[] classBytes = writer.toByteArray();
        CheckClassAdapter.verify(new ClassReader(classBytes), false, new PrintWriter(System.err));
        Files.write(getOutput().toPath(), classBytes);
        return 0;
    }

    void obfuscate(final ClassReader reader, final ClassWriter writer) {
        // Expanded frames are required for LocalVariablesSorter.
        reader.accept(new ObfuscatingClassVisitor(Opcodes.ASM9, writer, symbolMapping, bootstrapMethodHandle),
            ClassReader.EXPAND_FRAMES);
    }

    void addBootstrapMethod(final ClassReader reader, final ClassWriter writer) {
        final var classVisitor = new BootstrappingClassVisitor(Opcodes.ASM9, writer, bootstrapMethodHandle);
        reader.accept(classVisitor, ClassReader.EXPAND_FRAMES);

        bootstrapMethodHandle = classVisitor.getBootstrapMethodHandle();
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

    public Handle getBootstrapMethodHandle() {
        return bootstrapMethodHandle;
    }

    public void setBootstrapMethodHandle(final Handle bootstrapMethodHandle) {
        this.bootstrapMethodHandle = bootstrapMethodHandle;
    }
}
