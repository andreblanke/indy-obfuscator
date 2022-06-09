# InDy Obfuscator

InDy Obfuscator, short for InvokeDynamic Obfuscator, is an open-source obfuscator for JVM bytecode utilizing a novel
obfuscation technique which attempts to hide a program's call graph.

It does so by replacing `invokevirtual` and `invokestatic` instructions with `invokedynamic` ones, delegating to a
bootstrap method at the first time of invocation which is responsible for determining the actual call site.

By implementing the bootstrap method in native code, the level of obfuscation is increased without sacrificing much
performance.

## Related projects

- [superblaubeere27/obfuscator](https://github.com/superblaubeere27/obfuscator), an open-source Java bytecode obfuscator
  supporting a variety of obfuscation techniques, including regular `invokedynamic` obfuscation.
- [Zelix KlassMaster](https://www.zelix.com/klassmaster/index.html), a commercial obfuscator for Java bytecode.

## Links

- [ASM 4.0 - A Java bytecode engineering library](https://asm.ow2.io/asm4-guide.pdf)
