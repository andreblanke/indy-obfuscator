package dev.blanke.indyobfuscator;

import java.io.PrintWriter;
import java.util.concurrent.Callable;
import java.util.jar.Attributes.Name;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.util.CheckClassAdapter;

import picocli.CommandLine;
import picocli.CommandLine.Mixin;

import dev.blanke.indyobfuscator.mapping.SequentialSymbolMapping;
import dev.blanke.indyobfuscator.mapping.SymbolMapping;
import dev.blanke.indyobfuscator.template.DataModel;
import dev.blanke.indyobfuscator.template.FreeMarkerTemplateEngine;
import dev.blanke.indyobfuscator.template.TemplateEngine;
import dev.blanke.indyobfuscator.visitor.bootstrap.BootstrapMethodConflictException;
import dev.blanke.indyobfuscator.visitor.bootstrap.BootstrappingClassVisitor;
import dev.blanke.indyobfuscator.visitor.field.FieldAccessWrappingClassVisitor;
import dev.blanke.indyobfuscator.visitor.obfuscation.ObfuscatingClassVisitor;

/**
 * The {@code InDyObfuscator} class serves as the entry point to the obfuscation tool via {@link #main(String...)} and
 * {@link #call()}.
 * <p>
 * It enables execution of each basic step of the obfuscation process (replacement of field accesses with method
 * invocations, {@code invokedynamic} obfuscation, and generation of the bootstrap method alongside library loading code
 * for its native implementation) on the level of a single class.
 * <p>
 * See {@link InputType} for higher-level logic of input file handling that goes beyond the processing of single class
 * files.
 * <p>
 * Prior to the invocation of the later two steps in the obfuscation process, the bootstrap method handle must be
 * assigned via {@link #addBootstrapMethod(ClassReader, ClassWriter)}, as the used handle is dependent upon the type
 * of input to be obfuscated.
 *
 * @see #wrapFieldAccesses(ClassReader, ClassWriter)
 * @see #obfuscate(ClassReader, ClassWriter)
 * @see #addBootstrapMethod(ClassReader, ClassWriter)
 */
public final class InDyObfuscator implements Callable<Integer> {

    /**
     * The parsed command-line arguments passed to the obfuscator.
     *
     * @implNote The {@code @Mixin} annotation allows this class to define {@link #call()} while keeping the fields
     *           representing options and parameters in the {@link Arguments} class.
     */
    @Mixin
    private Arguments arguments;

    /**
     * A reference to the bootstrap method to which {@code invokedynamic} instructions delegate.
     * <p>
     * The owner of the bootstrap method depends on the obfuscation taking place: if a class file is being obfuscated,
     * that class will also be the owner of the bootstrap method. In case a jar file is being obfuscated,
     * the main class will be the owner unless a different owner is specified using command-line arguments.
     */
    private Handle bootstrapMethodHandle;

    // region Verification
    /**
     * Whether the transformed class files should be verified using ASM's {@link CheckClassAdapter}.
     *
     * @see #acceptAndVerify(ClassReader, ClassWriter, ClassVisitor)
     */
    private final boolean verify;

    /**
     * A writer to which the verification results and encountered errors will be written.
     *
     * @see #acceptAndVerify(ClassReader, ClassWriter, ClassVisitor)
     */
    private final PrintWriter verificationResultsPrintWriter;
    // endregion

    /**
     * Stores the mapping of unique identifiers to {@link dev.blanke.indyobfuscator.mapping.MethodInvocation}s.
     * <p>
     * The unique identifier associated with a {@code MethodInvocation} is used for the {@code invokedynamic}
     * instruction and then passed to the bootstrap method. The bootstrap utilities the unique identifier for returning
     * the correct {@link java.lang.invoke.CallSite} emulating the original {@code MethodInvocation}
     * <p>
     * The {@code SymbolTable} is populated during calls to {@link #obfuscate(ClassReader, ClassWriter)} and later
     * passed to the {@link #templateEngine} as part of the {@link DataModel} to populate the bootstrap method template.
     *
     * @see #obfuscate(ClassReader, ClassWriter)
     */
    private final SymbolMapping symbolMapping = new SequentialSymbolMapping();

    /**
     * The template engine which, given a {@link DataModel} containing information about the bootstrap method and the
     * {@link #symbolMapping}, processes the provided bootstrap method template in order to output source code for the
     * bootstrap method which is ready for compilation.
     * <p>
     * No templating happens unless the {@code --bsm-template} command-line argument is passed to the obfuscator.
     *
     * @see #call()
     */
    private final TemplateEngine templateEngine = new FreeMarkerTemplateEngine();

    /**
     * Instantiates a new {@code InDyObfuscator} object.
     *
     * @param verify Whether the transformed class files should be verified using ASM's {@link CheckClassAdapter}.
     */
    public InDyObfuscator(final boolean verify) {
        //noinspection AssignmentUsedAsCondition
        verificationResultsPrintWriter = (this.verify = verify) ? new PrintWriter(System.err) : null;
    }

    /**
     * Launches the obfuscation tool by delegating command-line argument parsing to Picocli, running the {@link #call()}
     * method, and exiting with the returned exit code.
     *
     * @param args The command-line arguments parsed into an {@link Arguments} instance by Picocli.
     */
    public static void main(final String... args) {
        final int exitCode = new CommandLine(new InDyObfuscator(false)).execute(args);
        System.exit(exitCode);
    }

    /**
     * Runs the obfuscation on the input by delegating to the correct {@link InputType#obfuscate(InDyObfuscator)}
     * implementation and populates the bootstrap method template if one is given.
     * <p>
     * The obfuscated artifact is output to the location specified by {@link Arguments#getOutput()} while the populated
     * bootstrap method template is written to {@link System#out}.
     * <p>
     * Any logging will be done to {@link System#err}.
     *
     * @return The exit code of the obfuscation tool.
     *
     * @throws Exception If an unexpected exception not associated with an exit code occurs.
     */
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

    /**
     * Replaces field instructions with the opcodes {@link Opcodes#GETFIELD}, {@link Opcodes#PUTFIELD},
     * {@link Opcodes#GETSTATIC}, and {@link Opcodes#PUTSTATIC} with method invocations to a synthetic accessor method
     * performing the actual field access.
     * <p>
     * This replacement process essentially allows field instructions to be included in a later
     * {@link #obfuscate(ClassReader, ClassWriter)} step.
     *
     * @param reader The {@link ClassReader} representing the class whose field instructions should be replaced with
     *               method instructions.
     *
     * @param writer The {@link ClassWriter} enabling output of the transformed class file.
     */
    public void wrapFieldAccesses(final ClassReader reader, final ClassWriter writer) {
        acceptAndVerify(reader, writer, new FieldAccessWrappingClassVisitor(writer));
    }

    /**
     * Replaces regular method instructions with the opcodes {@link Opcodes#INVOKESPECIAL}, {@link Opcodes#INVOKESPECIAL},
     * {@link Opcodes#INVOKESTATIC}, and {@link Opcodes#INVOKEINTERFACE} with {@link Opcodes#INVOKEDYNAMIC} instructions
     * as the main obfuscation step.
     * <p>
     * The generated {@code invokedynamic} instructions delegate to the {@link #bootstrapMethodHandle} in order to
     * retrieve the actual {@link java.lang.invoke.CallSite} to invoke.
     *
     * @param reader The {@link ClassReader} representing the class whose method instructions should be obfuscated.
     *
     * @param writer The {@link ClassWriter} enabling output of the transformed class file.
     */
    public void obfuscate(final ClassReader reader, final ClassWriter writer) {
        acceptAndVerify(reader, writer, new ObfuscatingClassVisitor(writer, symbolMapping, bootstrapMethodHandle));
    }

    /**
     * Adds the bootstrap method definition along with library loading code required for its native implementation to
     * the class represented by the provided {@code reader}.
     * <p>
     * The library loading code will be prepended to the {@code <clinit>} method of the class, creating it if it does
     * not yet exist.
     *
     * @param reader The {@link ClassReader} representing the class which should contain the bootstrap method and the
     *               library loading code required by it.
     *
     * @param writer The {@link ClassWriter} enabling output of the transformed class file.
     */
    public void addBootstrapMethod(final ClassReader reader, final ClassWriter writer) {
        acceptAndVerify(reader, writer, new BootstrappingClassVisitor(writer, bootstrapMethodHandle));
    }

    /**
     * Makes the {@code visitor} visit the provided {@code reader} in order to transform the class bytes, verifying the
     * output bytes if verification is enabled for this instance.
     *
     * @param reader The {@link ClassReader} that should accept the provided {@code visitor}.
     *
     * @param writer The {@link ClassWriter} storing the transformed class. Transformed class bytes are fed back into
     *               a {@code ClassReader} for the purpose of invoking
     *               {@link CheckClassAdapter#verify(ClassReader, boolean, PrintWriter)}.
     *
     * @param visitor A visitor modifying the original class from the {@code reader}, storing the transformed bytes
     *                in the {@code writer}.
     *
     * @see #verify
     */
    private void acceptAndVerify(final ClassReader reader, final ClassWriter writer, ClassVisitor visitor) {
        if (verify)
            visitor = new CheckClassAdapter(visitor);
        reader.accept(visitor, ClassReader.EXPAND_FRAMES);

        if (verify) {
            CheckClassAdapter.verify(new ClassReader(writer.toByteArray()), true, verificationResultsPrintWriter);
        }
    }

    // region Getters/setters
    Arguments getArguments() {
        return arguments;
    }

    Handle getBootstrapMethodHandle() {
        return bootstrapMethodHandle;
    }

    void setBootstrapMethodHandle(final Handle bootstrapMethodHandle) {
        this.bootstrapMethodHandle = bootstrapMethodHandle;
    }
    // endregion
}
