<#-- @ftlvariable name="dataModel" type="dev.blanke.indyobfuscator.template.DataModel" -->
#include <stdlib.h>
#include <stdio.h>

#include <jni.h>

#define FIELD_INSN_HANDLE_OBFUSCATION <#if dataModel.fieldObfuscationMode().name() == "METHOD_HANDLES">1<#else>0</#if>

#ifdef FIELD_INSN_HANDLE_OBFUSCATION
#define OPCODE_GETSTATIC       ${Opcodes.GETSTATIC}
#define OPCODE_PUTSTATIC       ${Opcodes.PUTSTATIC}
#define OPCODE_GETFIELD        ${Opcodes.GETFIELD}
#define OPCODE_PUTFIELD        ${Opcodes.PUTFIELD}
#endif

#define OPCODE_INVOKEVIRTUAL   ${Opcodes.INVOKEVIRTUAL}
#define OPCODE_INVOKESPECIAL   ${Opcodes.INVOKESPECIAL}
#define OPCODE_INVOKESTATIC    ${Opcodes.INVOKESTATIC}
#define OPCODE_INVOKEINTERFACE ${Opcodes.INVOKEINTERFACE}

static const jint JNI_VERSION = JNI_VERSION_1_8;

// Cache jclass and jmethodID references.

static jclass    ConstantCallSite;
static jmethodID ConstantCallSite_Init;

static jclass    Lookup;
static jmethodID Lookup_FindVirtual;
static jmethodID Lookup_FindSpecial;
static jmethodID Lookup_FindStatic;
#if FIELD_INSN_HANDLE_OBFUSCATION
static jmethodID Lookup_FindGetter;
static jmethodID Lookup_FindSetter;
static jmethodID Lookup_FindStaticGetter;
static jmethodID Lookup_FindStaticSetter;
#endif

static jclass    MethodType;
static jmethodID MethodType_DropParameterTypes;
#if FIELD_INSN_HANDLE_OBFUSCATION
static jmethodID MethodType_ReturnType;
static jmethodID MethodType_ParameterType;
#endif

static void check_jni_exception(JNIEnv *env)
{
    if ((*env)->ExceptionCheck(env) == JNI_TRUE) {
        (*env)->ExceptionDescribe(env);
    }
}

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved)
{
    JNIEnv *env;
    if ((*vm)->GetEnv(vm, (void **) &env, JNI_VERSION) != JNI_OK) {
        return JNI_ERR;
    }

    ConstantCallSite = (*env)->NewGlobalRef(env, (*env)->FindClass(env, "java/lang/invoke/ConstantCallSite"));
    ConstantCallSite_Init =
        (*env)->GetMethodID(env, ConstantCallSite, "<init>", "(Ljava/lang/invoke/MethodHandle;)V");

    Lookup = (*env)->NewGlobalRef(env, (*env)->FindClass(env, "java/lang/invoke/MethodHandles$Lookup"));
    Lookup_FindVirtual = (*env)->GetMethodID(env, Lookup, "findVirtual",
        "(Ljava/lang/Class;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/MethodHandle;");
    Lookup_FindSpecial = (*env)->GetMethodID(env, Lookup, "findSpecial",
        "(Ljava/lang/Class;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/Class;)Ljava/lang/invoke/MethodHandle;");
    Lookup_FindStatic = (*env)->GetMethodID(env, Lookup, "findStatic",
        "(Ljava/lang/Class;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/MethodHandle;");
#if FIELD_INSN_HANDLE_OBFUSCATION
    Lookup_FindGetter = (*env)->GetMethodID(env, Lookup, "findGetter",
        "(Ljava/lang/Class;Ljava/lang/String;Ljava/lang/Class;)Ljava/lang/invoke/MethodHandle;");
    Lookup_FindSetter = (*env)->GetMethodID(env, Lookup, "findSetter",
        "(Ljava/lang/Class;Ljava/lang/String;Ljava/lang/Class;)Ljava/lang/invoke/MethodHandle;");
    Lookup_FindStaticGetter = (*env)->GetMethodID(env, Lookup, "findStaticGetter",
        "(Ljava/lang/Class;Ljava/lang/String;Ljava/lang/Class;)Ljava/lang/invoke/MethodHandle;");
    Lookup_FindStaticSetter = (*env)->GetMethodID(env, Lookup, "findStaticSetter",
        "(Ljava/lang/Class;Ljava/lang/String;Ljava/lang/Class;)Ljava/lang/invoke/MethodHandle;");
#endif

    MethodType = (*env)->NewGlobalRef(env, (*env)->FindClass(env, "java/lang/invoke/MethodType"));
    MethodType_DropParameterTypes =
        (*env)->GetMethodID(env, MethodType, "dropParameterTypes", "(II)Ljava/lang/invoke/MethodType;");
#if FIELD_INSN_HANDLE_OBFUSCATION
    MethodType_ReturnType =
        (*env)->GetMethodID(env, MethodType, "returnType", "()Ljava/lang/Class;");
    MethodType_ParameterType =
        (*env)->GetMethodID(env, MethodType, "parameterType", "(I)Ljava/lang/Class;");
#endif

    return JNI_VERSION;
}

static jobject resolve(JNIEnv *env, jobject lookup, const char *ownerName, int opcode, const char *invokedName,
    jobject invokedType, const char *callerName)
{
    jclass owner = (*env)->FindClass(env, ownerName);
    check_jni_exception(env);

    jstring name = (*env)->NewStringUTF(env, invokedName);
    check_jni_exception(env);

    jobject fieldType;
    jobject methodHandle;
    switch (opcode) {
        case OPCODE_INVOKEVIRTUAL:
        case OPCODE_INVOKEINTERFACE:
            // Drop receiver parameter.
            invokedType = (*env)->CallObjectMethod(env, invokedType, MethodType_DropParameterTypes, 0, 1);
            check_jni_exception(env);

            methodHandle = (*env)->CallObjectMethod(env, lookup, Lookup_FindVirtual, owner, name, invokedType);
            break;
        case OPCODE_INVOKESPECIAL:
            // Drop receiver parameter.
            invokedType = (*env)->CallObjectMethod(env, invokedType, MethodType_DropParameterTypes, 0, 1);
            check_jni_exception(env);

            // Find caller class.
            jclass caller = (*env)->FindClass(env, callerName);

            methodHandle = (*env)->CallObjectMethod(env, lookup, Lookup_FindSpecial, owner, name, invokedType, caller);
            break;
        case OPCODE_INVOKESTATIC:
            methodHandle = (*env)->CallObjectMethod(env, lookup, Lookup_FindStatic, owner, name, invokedType);
            break;
#if FIELD_INSN_HANDLE_OBFUSCATION
        case OPCODE_GETFIELD:
            fieldType    = (*env)->CallObjectMethod(env, invokedType, MethodType_ReturnType);
            methodHandle = (*env)->CallObjectMethod(env, lookup, Lookup_FindGetter, owner, name, fieldType);
            break;
        case OPCODE_GETSTATIC:
            fieldType    = (*env)->CallObjectMethod(env, invokedType, MethodType_ReturnType);
            methodHandle = (*env)->CallObjectMethod(env, lookup, Lookup_FindStaticGetter, owner, name, fieldType);
            break;
        case OPCODE_PUTFIELD:
            fieldType    = (*env)->CallObjectMethod(env, invokedType, MethodType_ParameterType, 1);
            methodHandle = (*env)->CallObjectMethod(env, lookup, Lookup_FindSetter, owner, name, fieldType);
            break;
        case OPCODE_PUTSTATIC:
            fieldType    = (*env)->CallObjectMethod(env, invokedType, MethodType_ParameterType, 0);
            methodHandle = (*env)->CallObjectMethod(env, lookup, Lookup_FindStaticSetter, owner, name, fieldType);
            break;
#endif
    }
    check_jni_exception(env);

    // Instantiate ConstantCallSite using the retrieved MethodHandle.
    jobject callSite = (*env)->NewObject(env, ConstantCallSite, ConstantCallSite_Init, methodHandle);
    check_jni_exception(env);
    return callSite;
}

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
    <#-- Caller is needed for MethodHandles.Lookup.findSpecial. -->
    <#if methodId.opcode() == Opcodes.INVOKESPECIAL>
        <#assign caller = "\"${methodId.caller()}\"">
    <#else>
        <#assign caller = "NULL">
    </#if>
    <#-- Make use of the OPCODE_* macros instead of passing magic numbers to the resolve function. -->
    <#switch methodId.opcode()>
        <#case Opcodes.INVOKEVIRTUAL>
            <#assign opcode = "OPCODE_INVOKEVIRTUAL">
            <#break>
        <#case Opcodes.INVOKESPECIAL>
            <#assign opcode = "OPCODE_INVOKESPECIAL">
            <#break>
        <#case Opcodes.INVOKESTATIC>
            <#assign opcode = "OPCODE_INVOKESTATIC">
            <#break>
        <#case Opcodes.INVOKEINTERFACE>
            <#assign opcode = "OPCODE_INVOKEINTERFACE">
            <#break>
        <#case Opcodes.GETFIELD>
            <#assign opcode = "OPCODE_GETFIELD">
            <#break>
        <#case Opcodes.PUTFIELD>
            <#assign opcode = "OPCODE_PUTFIELD">
            <#break>
        <#case Opcodes.GETSTATIC>
            <#assign opcode = "OPCODE_GETSTATIC">
            <#break>
        <#case Opcodes.PUTSTATIC>
            <#assign opcode = "OPCODE_PUTSTATIC">
            <#break>
    </#switch>
    case ${mapping.getValue()}:
        return resolve(env, lookup, "${methodId.owner()}", ${opcode}, "${methodId.name()}", invokedType, ${caller});
    </#list>
    default:
        return NULL;
    }
}
