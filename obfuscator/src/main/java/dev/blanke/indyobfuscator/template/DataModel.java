package dev.blanke.indyobfuscator.template;

import org.objectweb.asm.Handle;

import dev.blanke.indyobfuscator.Arguments.FieldObfuscationMode;
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
 *
 * @param symbolMapping The set of obfuscated {@link dev.blanke.indyobfuscator.mapping.MethodInvocation}s along with
 *                      their assigned identifier.
 *                      <p>
 *                      The bootstrap method template should use this information to generate a bootstrap method
 *                      implementation which given an identifier assigned to a {@code MethodInvocation} returns a
 *                      {@link java.lang.invoke.CallSite} instance that emulates the original {@code MethodInvocation}.
 *
 * @param fieldObfuscationMode The obfuscation mode used for field instructions.
 *                             <p>
 *                             The bootstrap method template can use this information to judge whether, e.g., additional
 *                             {@code jmethodID} references to methods of {@link java.lang.invoke.MethodHandles.Lookup}
 *                             have to be cached, such as {@code findSetter}, {@code findGetter}, or their static
 *                             equivalent.
 */
public record DataModel(Handle bootstrapMethodHandle, SymbolMapping symbolMapping,
                        FieldObfuscationMode fieldObfuscationMode) {}
