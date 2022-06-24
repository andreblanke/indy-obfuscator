#include <cstdio>
#include <cstdlib>

#include <jni.h>

int main(int argc, char *argv[])
{
    if (argc != 2) {
        printf("usage: %s CLASS_NAME\n", argv[0]);
        return EXIT_FAILURE;
    }

    JavaVMInitArgs vm_args;
    JavaVMOption* options = new JavaVMOption[1];
    options[0].optionString = "-Djava.class.path=target/indy-obfuscator-1.0-SNAPSHOT.obf.jar";
    vm_args.version            = JNI_VERSION_1_6;
    vm_args.nOptions           = 1;
    vm_args.options            = options;
    vm_args.ignoreUnrecognized = false;

    // Load and initialize a Java VM, return a JNI interface pointer in env.
    JavaVM *jvm;
    JNIEnv *env;
    JNI_CreateJavaVM(&jvm, (void**) &env, &vm_args);
    delete options;

    jclass clazz = env->FindClass(argv[1]);
    printf("%p\n", clazz);

    jvm->DestroyJavaVM();
}
