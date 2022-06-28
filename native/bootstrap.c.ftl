<#-- @ftlvariable name="dataModel" type="dev.blanke.indyobfuscator.template.DataModel" -->
#include <stdlib.h>
#include <stdio.h>

#include <jni.h>

#include "debug.h"

// Keep references to jclass and jmethodID structs which might be accessed on invocation of the bootstrap method.

static jclass    ConstantCallSite;
static jmethodID ConstantCallSiteInit;

static jmethodID LookupFindVirtual;
static jmethodID LookupFindStatic;

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved)
{
    JNIEnv *env;
    if ((*vm)->GetEnv(vm, (void **) &env, JNI_VERSION_1_8) != JNI_OK) {
        return JNI_ERR;
    }

    // NewGlobalRef is used to prevent garbage collection of the instances.

    ConstantCallSite     = (*env)->NewGlobalRef(env, (*env)->FindClass(env, "java/lang/invoke/ConstantCallSite"));
    ConstantCallSiteInit = (*env)->GetMethodID(env, ConstantCallSite, "<init>", "(Ljava/lang/invoke/MethodHandle;)V");

    jclass lookup = (*env)->FindClass(env, "java/lang/invoke/MethodHandles$Lookup");

    LookupFindVirtual = (*env)->GetMethodID(env, lookup, "findVirtual",
        "(Ljava/lang/Class;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/MethodHandle;");
    LookupFindStatic = (*env)->GetMethodID(env, lookup, "findStatic",
        "(Ljava/lang/Class;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/MethodHandle;");
    return JNI_VERSION_1_8;
}

static jobject Resolve
    (JNIEnv *env, jobject lookup, const char *ownerName, const char *invokedName, jobject invokedType, int opcode)
{
    static jmethodID DropParameterTypes = NULL;
    if (DropParameterTypes == NULL)
    {
        jclass methodType = (*env)->FindClass(env, "java/lang/invoke/MethodType");
        DropParameterTypes = (*env)->GetMethodID(env, methodType, "dropParameterTypes",
            "(II)Ljava/lang/invoke/MethodType;");
    }

    printf("Resolving %s#%s\n", ownerName, invokedName);

    /*
     * TODO: Fix FindClass returning NULL for some reason.
     *       See:
     *         - https://stackoverflow.com/questions/13263340/findclass-from-any-thread-in-android-jni
     *         - https://android-developers.narkive.com/hEnu9mFP/jni-findclass-returns-null
     */
    jclass  owner = (*env)->FindClass(env, ownerName);
    jstring name  = (*env)->NewStringUTF(env, invokedName);

    jmethodID lookupMethod;
    switch (opcode) {
        case ${Opcodes.INVOKEVIRTUAL}:
        case ${Opcodes.INVOKEINTERFACE}:
            lookupMethod = LookupFindVirtual;
            invokedType = (*env)->CallObjectMethod(env, invokedType, DropParameterTypes, 0, 1);
            break;
        case ${Opcodes.INVOKESTATIC}:
            lookupMethod = LookupFindStatic;
            break;
    }

    PrintLn(env, ToString(env, invokedType));
    jobject methodHandle = (*env)->CallObjectMethod(env, lookup, lookupMethod, owner, name, invokedType);
    return (*env)->NewObject(env, ConstantCallSite, ConstantCallSiteInit, methodHandle);
}

<#--
    "${dataModel.bootstrapMethodHeader()}" could be used instead of manually building the correct header but would hide
    the parameters inside the template.
-->
<#assign bootstrapMethodOwner = dataModel.bootstrapMethodHandle().getOwner()?replace("/", "_")>
<#assign bootstrapMethodName  = dataModel.bootstrapMethodHandle().getName()>
JNIEXPORT jobject JNICALL Java_${bootstrapMethodOwner}_${bootstrapMethodName}
    (JNIEnv *env, jclass thisClass, jobject lookup, jstring invokedName, jobject invokedType)
{
    const char *invokedNameUTF = (*env)->GetStringUTFChars(env, invokedName, NULL);
    const long  invokedId      = strtol(invokedNameUTF, NULL, 0);
    (*env)->ReleaseStringUTFChars(env, invokedName, invokedNameUTF);

    switch (invokedId) {
    <#list dataModel.symbolMapping() as mapping>
    <#assign methodId = mapping.getKey()>
    case ${mapping.getValue()}:
        return Resolve(env, lookup, "${methodId.getOwner()}", "${methodId.getName()}", invokedType, ${methodId.getOpcode()});
    </#list>
    default:
        return NULL;
    }
}
