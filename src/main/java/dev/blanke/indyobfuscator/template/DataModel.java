package dev.blanke.indyobfuscator.template;

import org.objectweb.asm.Handle;

import dev.blanke.indyobfuscator.mapping.SymbolMapping;

public record DataModel(Handle bootstrapMethodHandle, SymbolMapping symbolMapping) {

    public String bootstrapMethodHeader() {
        return """
            JNIEXPORT jobject JNICALL Java_%s_%s
                (JNIEnv *env, jobject thisObject, jobject lookup, jstring invokedName, jobject invokedType)
            """.formatted(bootstrapMethodHandle.getOwner().replace('/', '_'), bootstrapMethodHandle.getName());
    }
}
