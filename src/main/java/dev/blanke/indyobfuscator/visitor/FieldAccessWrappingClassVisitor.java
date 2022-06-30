package dev.blanke.indyobfuscator.visitor;

import java.util.stream.Stream;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import dev.blanke.indyobfuscator.visitor.FieldAccessorMethodVisitor.*;

import static org.objectweb.asm.Opcodes.*;

public final class FieldAccessWrappingClassVisitor extends ClassVisitor {

    /**
     * Name of the class currently being visited which is passed to created {@link FieldAccessWrappingMethodVisitor}
     * instances.
     *
     * @see #visitMethod(int, String, String, String, String[])
     */
    private String className;

    private final Stream.Builder<FieldAccessWrappingMethodVisitor> methodVisitors = Stream.builder();

    public FieldAccessWrappingClassVisitor(final ClassVisitor classVisitor) {
        super(Opcodes.ASM9, classVisitor);
    }

    @Override
    public void visit(final int version, final int access, final String name, final String signature,
                      final String superName, final String[] interfaces) {
        super.visit(version, access, (className = name), signature, superName, interfaces);
    }

    @Override
    public MethodVisitor visitMethod(final int access, final String name, final String descriptor,
                                     final String signature, final String[] exceptions) {
        final var visitor = new FieldAccessWrappingMethodVisitor(api,
            super.visitMethod(access, name, descriptor, signature, exceptions), className);
        methodVisitors.accept(visitor);
        return visitor;
    }

    @Override
    public void visitEnd() {
        super.visitEnd();

        methodVisitors
            .build()
            .flatMap(FieldAccessWrappingMethodVisitor::fieldAccessors)
            .distinct()
            .forEach(fieldAccessor -> {
                var methodVisitor =
                    super.visitMethod(fieldAccessor.getAccess(), fieldAccessor.getName(), fieldAccessor.getDescriptor(),
                        null, new String[0]);
                methodVisitor = switch (fieldAccessor.getFieldOpcode()) {
                    case GETFIELD, GETSTATIC -> new FieldGetterMethodVisitor(api, methodVisitor, fieldAccessor);
                    case PUTFIELD, PUTSTATIC -> new FieldSetterMethodVisitor(api, methodVisitor, fieldAccessor);
                    default -> throw new IllegalArgumentException();
                };
                methodVisitor.visitCode();
                methodVisitor.visitMaxs(0, 0);
                methodVisitor.visitEnd();
            });
    }
}
