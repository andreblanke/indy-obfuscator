#include <jni.h>

#pragma clang diagnostic push
#pragma ide diagnostic ignored "bugprone-macro-parentheses"
#define CacheGlobalClassRef(varName, className) \
    static jclass varName = NULL; \
    if (varName == NULL) \
    { \
        varName = (*env)->NewGlobalRef(env, (*env)->FindClass(env, (className))); \
    }

#define CacheMethodID(varName, class, methodName, methodSig) \
    static jmethodID varName = NULL; \
    if (varName == NULL) \
    { \
        varName = (*env)->GetMethodID(env, class, methodName, methodSig); \
    }

#define CacheStaticFieldID(varName, class, fieldName, fieldSig) \
    static jfieldID varName = NULL; \
    if (varName == NULL) \
    { \
        varName = (*env)->GetStaticFieldID(env, class, fieldName, fieldSig); \
    }
#pragma clang diagnostic pop

static jstring ToString(JNIEnv *env, jobject obj)
{
    CacheGlobalClassRef(Object, "java/lang/Object")
    CacheMethodID(ToString, Object, "toString", "()Ljava/lang/String;")
    return (jstring) (*env)->CallObjectMethod(env, obj, ToString);
}

static void PrintLn(JNIEnv *env, jstring str)
{
    CacheGlobalClassRef(System, "java/lang/System")
    CacheStaticFieldID(Out, System, "out", "Ljava/io/PrintStream;")
    jobject out = (*env)->GetStaticObjectField(env, System, Out);

    CacheGlobalClassRef(PrintStream, "java/io/PrintStream")
    CacheMethodID(PrintLn, PrintStream, "println", "(Ljava/lang/String;)V")
    (*env)->CallVoidMethod(env, out, PrintLn, str);
}
