package top.dreamlike.unsafe.helper;

public class LambdaHelper {

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
}
