# Debugging JNI code

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
