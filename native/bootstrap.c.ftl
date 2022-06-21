<#-- @ftlvariable name="dataModel" type="dev.blanke.indyobfuscator.template.DataModel" -->
#include <stdlib.h>

#include <jni.h>

/*
 * Keep a reference to the ConstantCallSite class and its constructor to avoid a lookup on every invocation of the
 * bootstrap method.
 */
static jclass    ConstantCallSite;
static jmethodID ConstantCallSiteInit;

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved)
{
    JNIEnv *env;
    if (vm->GetEnv(vm, (void **) &env, JNI_VERSION_1_10) != JNI_OK) {
        return JNI_ERR;
    }

    ConstantCallSite     = env->FindClass(env, "java/lang/invoke/ConstantCallSite");
    ConstantCallSite     = (jclass) env->NewGlobalRef(ConstantCallSite); // Prevents garbage collection of the instance.
    ConstantCallSiteInit = env->GetMethodID(env, ConstantCallSite, "<init>", "()V");
    ConstantCallSiteInit = (jmethodID) env->NewGlobalRef(ConstantCallSiteInit);
    return JNI_VERSION_1_10;
}

<#--
    "${dataModel.bootstrapMethodHeader()}" could be used instead of manually building the correct header but would hide
    the parameters inside the template.
-->
<#assign bootstrapMethodOwner = dataModel.bootstrapMethodHandle().getOwner().replace("/", "_")>
<#assign bootstrapMethodName  = dataModel.bootstrapMethodHandle().getName()>
JNIEXPORT jobject JNICALL Java_${bootstrapMethodOwner}_${bootstrapMethodName}
    (JNIEnv *env, jclass thisClass, jobject lookup, jstring invokedName, jobject invokedType)
{
    jobject callSite = (*env)->NewObject(env, ConstantCallSite, ConstantCallSiteInit);

    const char *mappingValue = env->GetStringUTFChars(invokedName, 0);
    switch (strtol(mappingValue, NULL, 0)) {
    <#list dataModel.symbolMapping() as mapping>
    case ${mapping.value}:
        return NULL;
    </#list>
    default:
        return NULL;
    }
}
