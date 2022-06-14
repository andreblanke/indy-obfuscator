package dev.blanke.indyobfuscator;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.invoke.CallSite;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.file.Files;
import java.util.concurrent.Callable;
import java.util.jar.Attributes.Name;
import java.util.jar.JarFile;

import org.jetbrains.annotations.Nullable;

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

    @Option(
        names       = { "--bootstrap-method-owner" },
        description = """
            Fully qualified name of a class from the jar file which should contain the bootstrap method.
            Defaults to Main-Class of jar file if unspecified.""",
        paramLabel  = "<fqcn>")
    private String bootstrapMethodOwner;

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
        return InputType.determine(input).obfuscate(this);
    }

    void addBootstrapMethod(final ClassReader reader, final ClassWriter writer) {
        final var classVisitor = new BootstrappingClassVisitor(Opcodes.ASM9, writer, bootstrapMethodHandle);
        reader.accept(classVisitor, ClassReader.EXPAND_FRAMES);

        bootstrapMethodHandle = classVisitor.getBootstrapMethodHandle();
    }

    byte[] obfuscate(final ClassReader reader, final ClassWriter writer) {
        // Expanded frames are required for LocalVariablesSorter.
        reader.accept(new ObfuscatingClassVisitor(Opcodes.ASM9, writer, symbolMapping, bootstrapMethodHandle),
            ClassReader.EXPAND_FRAMES);

        final byte[] classBytes = writer.toByteArray();
        CheckClassAdapter.verify(new ClassReader(classBytes), false, new PrintWriter(System.err));
        return classBytes;
    }

    public File getInput() {
        return input;
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

    public String getBootstrapMethodOwner() {
        return bootstrapMethodOwner;
    }

    public Handle getBootstrapMethodHandle() {
        return bootstrapMethodHandle;
    }

    public void setBootstrapMethodHandle(final Handle bootstrapMethodHandle) {
        this.bootstrapMethodHandle = bootstrapMethodHandle;
    }

    private enum InputType {

        CLASS {
            @Override
            int obfuscate(final InDyObfuscator obfuscator) throws IOException {
                final var reader = new ClassReader(new FileInputStream(obfuscator.getInput()));
                final var writer = new ClassWriter(reader, 0);

                obfuscator.setBootstrapMethodHandle(new Handle(Opcodes.H_INVOKESTATIC, reader.getClassName(),
                    BOOTSTRAP_METHOD_DEFAULT_NAME, BOOTSTRAP_METHOD_DESCRIPTOR, false));
                obfuscator.addBootstrapMethod(reader, writer);

                final byte[] classBytes = obfuscator.obfuscate(reader, writer);
                Files.write(obfuscator.getOutput().toPath(), classBytes);
                return 0;
            }
        },

        JAR {
            @Override
            int obfuscate(final InDyObfuscator obfuscator) throws IOException {
                final var jarFile = new JarFile(obfuscator.getInput());

                final var bootstrapMethodOwner = getBootstrapMethodOwner(obfuscator, jarFile);
                if (bootstrapMethodOwner == null) {
                    System.err.println("""
                        No 'Main-Class' attribute found in MANIFEST.MF.
                        Please specify the bootstrap method owner manually using the --bootstrap-method-owner option.
                        """);
                    return 1;
                }

                obfuscator.setBootstrapMethodHandle(new Handle(Opcodes.H_INVOKESTATIC, bootstrapMethodOwner,
                    BOOTSTRAP_METHOD_DEFAULT_NAME, BOOTSTRAP_METHOD_DESCRIPTOR, false));
                return 0;
            }

            private @Nullable String getBootstrapMethodOwner(final InDyObfuscator obfuscator, final JarFile jarFile)
                    throws IOException {
                if (obfuscator.getBootstrapMethodOwner() != null)
                    return obfuscator.getBootstrapMethodOwner();
                return jarFile.getManifest().getMainAttributes().getValue(Name.MAIN_CLASS);
            }
        };

        abstract int obfuscate(InDyObfuscator obfuscator) throws IOException;

        public static InputType determine(final File file) throws IOException {
            try (final var inputStream = new DataInputStream(new FileInputStream(file))) {
                // Check for the magic number of .class files and treat input as jar file if it is not a .class file.
                return (inputStream.readInt() == 0xCAFEBABE) ? CLASS : JAR;
            }
        }
    }
}
