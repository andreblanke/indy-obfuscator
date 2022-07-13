# Debug

This folder contains debugging utilities.

## Debugging JNI code

1. Add a breakpoint for `dev.blanke.indyobfuscator.InDyObfuscator.<clinit>`
2. Launch the debug session which will hit the breakpoint before an attempt is made at
   loading the native bootstrap method library
3. Use `jps` to find out the process id
    - On UNIX systems `jps | sed -En 's/([0-9]+)\s+indy-obfuscator-1.0-SNAPSHOT.obf.jar/\1/p'` can
      be used to filter for the correct process id
4. Add a breakpoint in the JNI code
5. Attach your C debugger to the output process id
    - In the case of CLion, "Attach to Process" (<kbd>Ctrl+Alt+5</kbd>) can be used for this
    - See https://www.jetbrains.com/help/clion/attaching-to-local-process.html#attach-to-local if
      attachment of the debugger failed due to `ptrace: Operation not permitted.`

## Utilities

All utilities should be executed from the project root. They assume the existence of
`target/indy-obfuscator-1.0-SNAPSHOT.obf.jar` which is the obfuscated version of the original Maven artifact produced
by `mvn package` and a subsequent invocation of the artifact:

```shell
# Attempts to obfuscate itself, outputting to "target/${ARTIFACT_NAME}.obf.jar".
java -jar target/indy-obfuscator-1.0-SNAPSHOT.jar target/indy-obfuscator-1.0-SNAPSHOT.jar \
  -o target/indy-obfuscator-1.0-SNAPSHOT.obf.jar
```

### `FindClass.cpp`

Checks whether a given class can be loaded from the obfuscated `target/indy-obfuscator-1.0-SNAPSHOT.obf.jar` JAR file
via JNI's `FindClass` without encountering a `java.lang.VerifyError`.

The majority of the code was adapted from the [Java Native Interface Specification: 5 - The Invocation API][1].

```shell
# Building
cd debug && make; cd ..

# Usage
LD_LIBRARY_PATH="${LD_LIBRARY_PATH}:/path-to-libjvm.so-parent" ./debug/a.out
```

### `FindClass.java`

Checks whether a given class can be loaded from the obfuscated `target/indy-obfuscator-1.0-SNAPSHOT.obf.jar` JAR file
via `ClassLoader.loadClass` without encountering a `java.lang.VerifyError`.

```shell
java debug/FindClass.java
```

[1]: https://docs.oracle.com/en/java/javase/18/docs/specs/jni/invocation.html#overview
