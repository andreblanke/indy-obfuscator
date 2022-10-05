package dev.blanke.indyobfuscator;

import java.lang.invoke.CallSite;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.util.StringJoiner;
import java.util.regex.Pattern;

import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

final class Arguments {

    private static final Pattern AGENT_ARGS_PATTERN =
        Pattern.compile("(?<owner>[_a-zA-Z]\\w*)\\.(?<name>[_a-zA-Z]\\w*)");

    Arguments(final String agentArgs) {
        final var matcher = AGENT_ARGS_PATTERN.matcher(agentArgs);
        if (!matcher.matches())
            throw new IllegalArgumentException();
        obfuscationBootstrapMethodOwner = matcher.group("owner");
        obfuscationBootstrapMethodName  = matcher.group("name");
    }

    private static final Class<?>[] BOOTSTRAP_METHOD_PARAMETER_TYPES = new Class[] {
        MethodHandles.Lookup.class,
        String.class,    // invokedName
        MethodType.class // invokedType
    };
    private Class<?>[] getBootstrapMethodParameterTypes() {
        return BOOTSTRAP_METHOD_PARAMETER_TYPES;
    }

    private String bootstrapMethodDescriptor;
    private String getBootstrapMethodDescriptor() {
        if (bootstrapMethodDescriptor == null) {
            final var joiner = new StringJoiner("", "(", ")" + Type.getDescriptor(CallSite.class));
            for (Class<?> parameterType : getBootstrapMethodParameterTypes())
                joiner.add(Type.getDescriptor(parameterType));
            bootstrapMethodDescriptor = joiner.toString();
        }
        return bootstrapMethodDescriptor;
    }

    // region ObfuscationBootstrapMethod
    private final String obfuscationBootstrapMethodOwner;
    private String getObfuscationBootstrapMethodOwner() {
        return obfuscationBootstrapMethodOwner;
    }

    private final String obfuscationBootstrapMethodName;
    private String getObfuscationBootstrapMethodName() {
        return obfuscationBootstrapMethodName;
    }

    private volatile Method bootstrapMethod;
    Method getObfuscationBootstrapMethod() throws ClassNotFoundException, NoSuchMethodException {
        if (bootstrapMethod == null) {
            synchronized (this) {
                if (bootstrapMethod == null) {
                    bootstrapMethod = Class.forName(getObfuscationBootstrapMethodOwner())
                        .getMethod(getObfuscationBootstrapMethodName(), getBootstrapMethodParameterTypes());
                }
            }
        }
        return bootstrapMethod;
    }

    Handle getObfuscationBootstrapMethodHandle() {
        return new Handle(Opcodes.H_INVOKESTATIC, getObfuscationBootstrapMethodOwner().replace('.', '/'),
            getObfuscationBootstrapMethodName(), getBootstrapMethodDescriptor(), false);
    }
    // endregion

    Handle getDecoratorBootstrapMethodHandle() {
        return new Handle(Opcodes.H_INVOKESTATIC, Type.getInternalName(BootstrapDecorator.class),
            "decoratedBootstrap", getBootstrapMethodDescriptor(), false);
    }
}
