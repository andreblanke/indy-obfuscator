<#-- @ftlvariable name="dataModel" type="dev.blanke.indyobfuscator.template.DataModel" -->
#include <stdlib.h>

#include <jni.h>

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

    ConstantCallSite = (*env)->NewGlobalRef(env,
        (jobject) (*env)->FindClass(env, "java/lang/invoke/ConstantCallSite"));
    ConstantCallSiteInit = (jmethodID) (*env)->NewGlobalRef(env,
        (jobject) (*env)->GetMethodID(env, ConstantCallSite, "<init>", "(Ljava/lang/invoke/MethodHandle;)V"));

    jclass lookup = (*env)->FindClass(env, "java/lang/invoke/MethodHandles$Lookup");

    LookupFindVirtual = (jmethodID) (*env)->NewGlobalRef(env,
        (jobject) (*env)->GetMethodID(env, lookup, "findVirtual",
            "(Ljava/lang/Class;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/MethodHandle;"));
    LookupFindStatic = (jmethodID) (*env)->NewGlobalRef(env,
        (jobject) (*env)->GetMethodID(env, lookup, "findStatic",
            "(Ljava/lang/Class;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/MethodHandle;"));
    return JNI_VERSION_1_8;
}

static jobject resolve
    (JNIEnv *env, jobject lookup, const char *ownerName, const char *invokedName, jobject invokedType, int opcode)
{
    jclass  owner = (*env)->FindClass(env, ownerName);
    jstring name  = (*env)->NewStringUTF(env, invokedName);

    jmethodID lookupMethod;
    switch (opcode) {
        case ${Opcodes.INVOKEVIRTUAL}:
        case ${Opcodes.INVOKEINTERFACE}:
            lookupMethod = LookupFindVirtual;
            break;
        case ${Opcodes.INVOKESTATIC}:
            lookupMethod = LookupFindStatic;
            break;
    }

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
        return resolve(env, lookup, "${methodId.owner()}", "${methodId.name()}", invokedType, ${methodId.opcode()});
    </#list>
    default:
        return NULL;
    }
}
