package top.dreamlike.unsafe.helper;

public class NativeHelper {

    @FunctionalInterface
    public interface ThrowableSupplier<T> {
        T get() throws Throwable;
    }

    public static <T> T throwable(ThrowableSupplier<T> supplier) {
        try {
            return supplier.get();
        }catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    public static String classToSig(Class c) {
        if (c.isArray()) {
            return STR."[\{classToSig(c.getComponentType())}";
        }
        return switch (c.getName()) {
            case "void" -> "V";
            case "boolean" -> "Z";
            case "byte" -> "B";
            case "char" -> "C";
            case "short" -> "S";
            case "int" -> "I";
            case "long" -> "J";
            case "float" -> "F";
            case "double" -> "D";
            default -> STR."L\{c.getName().replace('.', '/')};";
        };
    }
}
