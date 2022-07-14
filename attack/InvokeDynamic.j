.version 51 0
.class public InvokeDynamic
.super java/lang/Object

; Reference to the current class
.const [thisclass] = Class InvokeDynamic

; Reference to the bootstrap method
.const [bootstrap] = Method [thisclass] bootstrap (Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;

.method public static main : ([Ljava/lang/String;)V
    .limit stack  0
    .limit locals 1

    invokedynamic InvokeDynamic invokeStatic [bootstrap] : target ()V
    return
.end method

.method public static bootstrap : (Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/CallSite;
    .limit stack  6
    .limit locals 3

    new java/lang/invoke/ConstantCallSite
    dup

    ; Find MethodHandle for target using MethodHandles$Lookup instance
    aload_0
    ; Push refc
    ldc [thisclass]
    ; Push name
    aload_1
    ; Push type
    aload_2

    invokevirtual java/lang/invoke/MethodHandles$Lookup findStatic (Ljava/lang/Class;Ljava/lang/String;Ljava/lang/invoke/MethodType;)Ljava/lang/invoke/MethodHandle;

    invokespecial java/lang/invoke/ConstantCallSite <init> (Ljava/lang/invoke/MethodHandle;)V
    areturn
.end method

.method public static target : ()V
    .limit stack  2
    .limit locals 0

    getstatic java/lang/System out Ljava/io/PrintStream;
    ldc "Hello, world!"
    invokevirtual java/io/PrintStream println (Ljava/lang/Object;)V
    return
.end method
