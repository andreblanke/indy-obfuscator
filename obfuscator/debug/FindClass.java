import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Scanner;

class FindClass {

    private static final File JAR_FILE = new File("target/indy-obfuscator-1.0-SNAPSHOT.obf.jar");

    private static final String DEFAULT_CLASS_NAME = "org.objectweb.asm.Type";

    public static void main(final String... args) {
        try (final var classLoader = new URLClassLoader(new URL[] { JAR_FILE.toURI().toURL() });
             final var scanner     = new Scanner(System.in)
        ) {
            System.out.printf("Enter the fully qualified name of a class from %s: [default: '%s']%n",
                JAR_FILE.getPath(), DEFAULT_CLASS_NAME);
            var className = scanner.nextLine();
            if (className.isBlank())
                className = DEFAULT_CLASS_NAME;

            final var clazz = classLoader.loadClass(className);
            System.out.println(clazz);
        } catch (final Exception exception) {
            exception.printStackTrace();
        }
    }
}
