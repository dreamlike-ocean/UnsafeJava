package top.dreamlike.unsafe.reflection;

import top.dreamlike.unsafe.core.MasterKey;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

public class Reflection {

    private static final MethodHandle GET_CALLER_CLASS_MH;

    public static Class<?> getCallerClass() {
        try {
            return (Class<?>) GET_CALLER_CLASS_MH.invokeExact();
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    static {
        try {
            MethodHandles.Lookup trustedLookup = MasterKey.INSTANCE.getTrustedLookup();

            Class<?> reflectionClass = trustedLookup.findClass("jdk.internal.reflect.Reflection");
            GET_CALLER_CLASS_MH = trustedLookup.findStatic(reflectionClass, "getCallerClass", MethodType.methodType(Class.class));

        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }
}
