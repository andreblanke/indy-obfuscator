package dev.blanke.indyobfuscator.template;

import org.intellij.lang.annotations.Language;

import org.objectweb.asm.Handle;

import dev.blanke.indyobfuscator.mapping.SymbolMapping;

public record DataModel(Handle bootstrapMethodHandle, SymbolMapping symbolMapping) {

    @SuppressWarnings("unused")
    public String bootstrapMethodHeader() {
        @Language("C")
        final var header = """
            JNIEXPORT jobject JNICALL Java_%s_%s
                (JNIEnv *env, jclass thisClass, jobject lookup, jstring invokedName, jobject invokedType)"""
            .formatted(bootstrapMethodHandle.getOwner().replace('/', '_'), bootstrapMethodHandle.getName());
        return header;
    }
}
