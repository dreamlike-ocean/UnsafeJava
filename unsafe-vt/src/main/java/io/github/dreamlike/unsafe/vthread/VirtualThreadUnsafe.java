package io.github.dreamlike.unsafe.vthread;



import top.dreamlike.unsafe.core.MasterKey;

import java.lang.invoke.LambdaMetafactory;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.function.Supplier;

public class VirtualThreadUnsafe {

    public static MethodHandles.Lookup IMPL_LOOKUP = MasterKey.INSTANCE.getTrustedLookup();

    public final static Function<Executor, Thread.Builder.OfVirtual> VIRTUAL_THREAD_BUILDER = fetchVirtualThreadBuilder();

    private final static Supplier<Thread> CARRIERTHREAD_SUPPLIER = carrierThreadSupplier();

    private static Function<Executor, Thread.Builder.OfVirtual> fetchVirtualThreadBuilder() {
        var tmp = Thread.ofVirtual().getClass();
        try {
            MethodHandle builderMethodHandle = MasterKey.INSTANCE.openTheDoor(tmp.getDeclaredConstructor(Executor.class));
            MethodHandle lambdaFactory = LambdaMetafactory.metafactory(
                    IMPL_LOOKUP,
                    "apply",
                    MethodType.methodType(Function.class),
                    MethodType.methodType(Object.class, Object.class),
                    builderMethodHandle,
                    builderMethodHandle.type()
            ).getTarget();
            return (Function<Executor, Thread.Builder.OfVirtual>) lambdaFactory.invoke();
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    private static Supplier<Thread> carrierThreadSupplier() {
        try {
            MethodHandle currentCarrierThreadMh = MasterKey.INSTANCE.openTheDoor(Thread.class.getDeclaredMethod("currentCarrierThread"));
            MethodHandle lambda = LambdaMetafactory.metafactory(
                    IMPL_LOOKUP,
                    "get",
                    MethodType.methodType(Supplier.class),
                    MethodType.methodType(Object.class),
                    currentCarrierThreadMh,
                    currentCarrierThreadMh.type()
            ).getTarget();
            return (Supplier<Thread>) lambda.invoke();
        }catch (Throwable throwable){
            throw new RuntimeException(throwable);
        }
    }

    public static Thread currentCarrierThread() {
        Thread thread = Thread.currentThread();
        return thread.isVirtual() ? CARRIERTHREAD_SUPPLIER.get() : thread;
    }

}
