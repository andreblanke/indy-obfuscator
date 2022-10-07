# InDy Obfuscator

InDy Obfuscator, short for InvokeDynamic Obfuscator, is an open-source obfuscator for JVM bytecode utilizing a novel
obfuscation technique which attempts to hide a program's call graph.

It does so by replacing `invoke(virtual|special|static|interface)` instructions with `invokedynamic` ones, delegating
to a bootstrap method at first invocation time. The bootstrap method is then responsible for determining the actual
call site.

By implementing the bootstrap method in native code rather than in Java itself, the level of obfuscation is increased
without sacrificing much performance. It also gives access to a broad variety of additional obfuscation methods for
native code.

## Usage

### With Docker

```shell
docker build -t indy-obfuscator . && docker run -it indy-obfuscator

java -jar obfuscator.jar -- obfuscator.jar -o obfuscator-obf.jar -I dev.blanke.indyobfuscator.* > bootstrap.c

mkdir cmake && cd cmake
cmake .. && make

mv libbootstrap.so .. && cd ..
java -jar obfuscator-obf.jar
```

### Without Docker

#### Obtaining the obfuscator

This project uses [Apache Maven](https://maven.apache.org/) as its build system. To compile the obfuscator from source,
first clone the repository and navigate to its root directory.

An executable JAR file can be created by executing

```shell
mvn package
```

The packaged artifact will be located inside the `target` directory.

#### Prerequisites

Due to the premise of the obfuscation technique, some non-Java related tools are required in addition to the obfuscator
itself. These tools are used to build a shared library that can be accessed using JNI. The following programs will be
needed:

- A C compiler (e.g. [GCC](https://gcc.gnu.org/), [Clang](https://clang.llvm.org/),
  or [MSVC](https://visualstudio.microsoft.com/vs/features/cplusplus/)), for the compilation of the bootstrap method
  source code generated from the bootstrap method template processing

- (optional, recommended) [CMake](https://cmake.org/), to compile the bootstrap method source code without manually
  locating `jni.h`

### Obfuscation process

Once all prerequisites have been met you can get started with the actual obfuscation process. Two input files are
required for this:

1. a JAR file to be obfuscated

2. a bootstrap method template

   An example bootstrap method template can be found at [native/bootstrap.c.ftl](src/main/resources/bootstrap.c.ftl).

See the diagram below for a high-level overview of the obfuscation process.

<p align="center">
    <img src="../figures/obfuscation-overview.png" alt="Overview of the obfuscation process">
</p>

<details>
<summary>Explanation of the obfuscation process</summary>

> The blue path represents the input file which gets obfuscated (or specific parts of it), while the orange path
> represents the processing of the bootstrap method template. The provided template file is populated using information
> gained in the obfuscation phase (most notably the symbol table and information about the bootstrap method) to produce
> valid source code making up the native implementation of the bootstrap method.
>
> The generated source code will be compiled into a native library which is loaded by the obfuscated program at runtime.
> As the compilation of the generated source code is out of scope for the obfuscator, this step will need to be executed
> manually or scripted.
>
> In the end, two artifacts will together make up the obfuscated program: an obfuscated jar or class file and a native
> library.
</details>

A simple example usage of the obfuscator is shown in the below command. `input.jar` is obfuscated while the example
bootstrap method template is populated.

`output.jar` will contain the obfuscated bytecode. The bootstrap method source code produced by the population of the
template will be written to `System.out`, so it is redirected to a file, in this case to `native/bootstrap.c`.

```shell
java -jar indy-obfuscator-1.0-SNAPSHOT.jar input.jar \
  -o output.jar \
  --bsm-template native/bootstrap.c.ftl > native/bootstrap.c
```

<details>
<summary>Further command-line arguments</summary>

- `--bsm-owner` can be used to manually specify the class which should contain the bootstrap method stub in case the
  owner cannot be determined automatically.

  By default, the main class of a jar file is used. This option has no effect when the input is a class file.

- `--bsm-name` can be used to manually specify the name of the bootstrap method in case a method with the same name
  and signature already exists within the class that should contain the bootstrap method.

- `-I` or `--include` can be used to specify one or more regular expressions matching fully qualified class names of
  classes to be included in the obfuscation.

  Non-confidential dependencies that require no obfuscation can and should be excluded from the obfuscation process
  by limiting the obfuscation to application-specific classes.

- `--help` can be used to show usage information and to list available command-line parameters.
</details>

The generated C source code making up the bootstrap method implementation must now be compiled to a shared library.
A CMake setup is included in the [native](native) folder of the repository for convenience but the compilation can also
be performed manually. An example usage of CMake to compile the `bootstrap.c` file output above is shown below.

```shell
cd native/cmake
cmake ..
make
```

Running the above commands should produce a shared library. The concrete name is platform-dependent: on UNIX systems for
example, the library will be named `libbootstrap.so` while it will be called `bootstrap.dll` on Windows.

Move the compiled library into the same folder as `output.jar`. The obfuscated program can now be launched using

```shell
java -jar output.jar
```

assuming that `input.jar` was an executable jar. Immediately after launch, the shared library will be loaded from the
current working directory.

See [obfuscate-self.sh](obfuscate-self.sh) for a script which builds the obfuscator, runs the obfuscator on its own jar
file, builds the shared library, and invokes the obfuscated obfuscator to show the usage information.

## Project structure

The below project layout gives an overview over the most important files and folders within this repository.

```text
├─ java-agent/               Dynamic analysis Java agent
│   └─ src/main/
│       └─ java/dev/blanke/indyobfuscator/
│           ├─ Arguments                 CLI arguments using Picocli
│           ├─ BootstrapDecorator        Implements decorated BSM
│           ├─ ClassFileTransformer      Changes indy instr. using ASM
│           └─ DynamicAnalysisAgent      premain entrypoint
├─ obfuscator/               Main obfuscator source code
│   ├─ native/
│   │   ├─ cmake/            CMake build for processed BSM template
│   │   └─ debug.h           Utility functions for debugging JNI code
│   └─ src/main/
│       ├─ java/dev/blanke/indyobfuscator/
│       │   ├─ mapping/                  Symbol mapping implementation
│       │   ├─ template/                 BSM template processing
│       │   ├─ obfuscation/              Bytecode obfuscation using ASM
│       │   │   ├─ bootstrap/            3rd obfuscation step
│       │   │   ├─ field/                1st obfuscation step
│       │   │   └─ obfuscation/          2nd obfuscation step
│       │   ├─ Arguments.java            CLI arguments using Picocli
│       │   ├─ InDyObfuscator.java       Main class
│       │   └─ InputType.java            Input JAR/class file handling
│       └─ resources/bootstrap.c.ftl     Default BSM template
└─ obfuscator-api/           Optional public API for applications
    └─ src/main/
        └─ java/dev/blanke/indyobfuscator/
            └─ Obfuscate.java            Annotation to limit obfuscation
```

## Related projects

- [superblaubeere27/obfuscator](https://github.com/superblaubeere27/obfuscator), an open-source Java bytecode obfuscator
  supporting a variety of obfuscation techniques, including regular `invokedynamic` obfuscation.
- [Zelix KlassMaster](https://www.zelix.com/klassmaster/index.html), a commercial obfuscator for Java bytecode.

## Links

- [ASM 4.0 - A Java bytecode engineering library](https://asm.ow2.io/asm4-guide.pdf)
- [Java Native Interface Specification: Contents](https://docs.oracle.com/en/java/javase/18/docs/specs/jni/)
