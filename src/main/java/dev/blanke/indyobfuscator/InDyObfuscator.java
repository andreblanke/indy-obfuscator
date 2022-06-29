package dev.blanke.indyobfuscator;

import java.io.PrintWriter;
import java.util.concurrent.Callable;
import java.util.jar.Attributes.Name;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Handle;
import org.objectweb.asm.util.CheckClassAdapter;

import picocli.CommandLine;
import picocli.CommandLine.Mixin;

import dev.blanke.indyobfuscator.mapping.SequentialSymbolMapping;
import dev.blanke.indyobfuscator.mapping.SymbolMapping;
import dev.blanke.indyobfuscator.template.DataModel;
import dev.blanke.indyobfuscator.template.FreeMarkerTemplateEngine;
import dev.blanke.indyobfuscator.template.TemplateEngine;
import dev.blanke.indyobfuscator.visitor.BootstrapMethodConflictException;
import dev.blanke.indyobfuscator.visitor.BootstrappingClassVisitor;
import dev.blanke.indyobfuscator.visitor.FieldAccessWrappingClassVisitor;
import dev.blanke.indyobfuscator.visitor.ObfuscatingClassVisitor;

public final class InDyObfuscator implements Callable<Integer> {

    @Mixin
    private Arguments arguments;

    /**
     * A reference to the bootstrap method to which {@code invokedynamic} instructions delegate.
     *
     * The owner of the bootstrap method depends on the obfuscation taking place: if a class file is being obfuscated,
     * that class will also be the owner of the bootstrap method. In case a jar file is being obfuscated,
     * the main class will be the owner unless a different owner is specified using command-line arguments.
     */
    private Handle bootstrapMethodHandle;

    private final boolean verify;

    private final PrintWriter verificationResultsPrintWriter;

    private final SymbolMapping symbolMapping = new SequentialSymbolMapping();

    private final TemplateEngine templateEngine = new FreeMarkerTemplateEngine();

    public InDyObfuscator(final boolean verify) {
        //noinspection AssignmentUsedAsCondition
        verificationResultsPrintWriter = (this.verify = verify) ? new PrintWriter(System.err) : null;
    }

    public static void main(final String... args) {
        final int exitCode = new CommandLine(new InDyObfuscator(false)).execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() throws Exception {
        try {
            InputType.determine(arguments.getInput()).obfuscate(this);

            if (arguments.getBootstrapMethodTemplate() != null) {
                templateEngine.process(arguments.getBootstrapMethodTemplate(),
                    new DataModel(bootstrapMethodHandle, symbolMapping), new PrintWriter(System.out));
            }
            return 0;
        } catch (final BootstrapMethodOwnerMissingException exception) {
            System.err.printf("""
                No '%s' attribute found inside the META-INF/MANIFEST.MF file.
                Please specify the bootstrap method owner manually using the --bootstrap-method-owner option.
                """, Name.MAIN_CLASS);
            return 1;
        } catch (final BootstrapMethodConflictException exception) {
            System.err.printf("""
                The bootstrap method name '%s' conflicts with an existing method inside the owning class '%s'.
                Please specify a different bootstrap method name using the --bootstrap-method-name option.
                """, bootstrapMethodHandle.getName(), bootstrapMethodHandle.getOwner().replace('/', '.'));
            return 2;
        }
    }

    public void wrapFieldAccesses(final ClassReader reader, final ClassWriter writer) {
        ClassVisitor visitor = new FieldAccessWrappingClassVisitor(writer);
        if (verify)
            visitor = new CheckClassAdapter(visitor);
        reader.accept(visitor, ClassReader.EXPAND_FRAMES);
        verifyIfEnabled(writer);
    }

    public void addBootstrapMethod(final ClassReader reader, final ClassWriter writer) {
        ClassVisitor visitor = new BootstrappingClassVisitor(writer, bootstrapMethodHandle);
        if (verify)
            visitor = new CheckClassAdapter(visitor);
        reader.accept(visitor, ClassReader.EXPAND_FRAMES);
        verifyIfEnabled(writer);
    }

    public void obfuscate(final ClassReader reader, final ClassWriter writer) {
        ClassVisitor visitor = new ObfuscatingClassVisitor(writer, symbolMapping, bootstrapMethodHandle);
        if (verify)
            visitor = new CheckClassAdapter(visitor);
        // Expanded frames are required for LocalVariablesSorter.
        reader.accept(visitor, ClassReader.EXPAND_FRAMES);
        verifyIfEnabled(writer);
    }

    private void verifyIfEnabled(final ClassWriter writer) {
        if (verify) {
            CheckClassAdapter.verify(new ClassReader(writer.toByteArray()), true, verificationResultsPrintWriter);
        }
    }

    public Arguments getArguments() {
        return arguments;
    }

    public Handle getBootstrapMethodHandle() {
        return bootstrapMethodHandle;
    }

    public void setBootstrapMethodHandle(final Handle bootstrapMethodHandle) {
        this.bootstrapMethodHandle = bootstrapMethodHandle;
    }
}
