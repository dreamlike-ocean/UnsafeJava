package io.github.dreamlike.unsafe.vthread;


import top.dreamlike.unsafe.core.MasterKey;

import java.lang.invoke.LambdaMetafactory;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.util.function.Supplier;

public class CarrierThreadLocal<T> extends ThreadLocal<T> {

    /**
     * 这玩意实际上是
     * @see jdk.internal.misc.CarrierThreadLocal
     */
    private final ThreadLocal<T> interlCarrierThreadLocal;

    private static Supplier<ThreadLocal> internalCarrierThreadLocalSupplier = hack();

    public CarrierThreadLocal() {
        interlCarrierThreadLocal = internalCarrierThreadLocalSupplier.get();
    }

    private static Supplier<ThreadLocal> hack() {
        try{
            String className = "jdk.internal.misc.CarrierThreadLocal";
            Class<?> clazz = Class.forName(className);
            MethodHandle constructorMethodHandle = MasterKey.INSTANCE.openTheDoor(clazz.getDeclaredConstructor());

            MethodHandle methodHandle = LambdaMetafactory.metafactory(
                    VirtualThreadUnsafe.IMPL_LOOKUP,
                    "get",
                    MethodType.methodType(Supplier.class),
                    MethodType.methodType(Object.class),
                    constructorMethodHandle,
                    constructorMethodHandle.type()
            ).getTarget();
            return (Supplier<ThreadLocal>) methodHandle.invoke();
        }catch (Throwable throwable){
            throw new RuntimeException(throwable);
        }
    }

    @Override
    public T get() {
        return interlCarrierThreadLocal.get();
    }

    @Override
    public void set(T value) {
        interlCarrierThreadLocal.set(value);
    }

    @Override
    public void remove() {
        interlCarrierThreadLocal.remove();
    }


}
