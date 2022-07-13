package dev.blanke.indyobfuscator.template;

import org.intellij.lang.annotations.Language;

import org.objectweb.asm.Handle;

import dev.blanke.indyobfuscator.mapping.SymbolMapping;

/**
 * The data model encapsulates fields that are available in the context of the bootstrap method template.
 *
 * @param bootstrapMethodHandle Contains information about the owner, name, and descriptor of the bootstrap method
 *                              defined on the Java side.
 *                              <p>
 *                              This information must be available to the native source code template to define the
 *                              correct function header for the native implementation of the bootstrap method,
 *                              as otherwise the JVM will be unable to match the {@code native} method stub on the Java
 *                              side with its implementation in the native code.
 *                              <p>
 *                              {@link #bootstrapFunctionHeader()} gives convenient access to a correct C function
 *                              header for the provided {@code bootstrapMethodHandle} from the bootstrap method
 *                              template.
 *
 * @param symbolMapping The set of obfuscated {@link dev.blanke.indyobfuscator.mapping.MethodInvocation}s along with
 *                      their assigned identifier.
 *                      <p>
 *                      The bootstrap method template should use this information to generate a bootstrap method
 *                      implementation which given an identifier assigned to a {@code MethodInvocation} returns a
 *                      {@link java.lang.invoke.CallSite} instance that emulates the original {@code MethodInvocation}.
 */
public record DataModel(Handle bootstrapMethodHandle, SymbolMapping symbolMapping) {

    /**
     * Convenience method for usage within the bootstrap method template which returns a string representing the C
     * function header for the JNI implementation of the bootstrap method, fitting to the provided
     * {@link #bootstrapMethodHandle}.
     *
     * @return A C function header matching the {@code bootstrapMethodHandle}.
     */
    @SuppressWarnings("unused")
    public String bootstrapFunctionHeader() {
        @Language("C")
        final var header = """
            JNIEXPORT jobject JNICALL Java_%s_%s
                (JNIEnv *env, jclass thisClass, jobject lookup, jstring invokedName, jobject invokedType)"""
            .formatted(bootstrapMethodHandle.getOwner().replace('/', '_'), bootstrapMethodHandle.getName());
        return header;
    }
}
