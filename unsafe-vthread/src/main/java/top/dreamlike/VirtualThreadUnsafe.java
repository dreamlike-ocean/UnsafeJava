package top.dreamlike;

import top.dreamlike.unsafe.jni.JNIEnv;
import top.dreamlike.unsafe.helper.GlobalRef;

import java.lang.foreign.Arena;
import java.lang.invoke.LambdaMetafactory;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.function.Supplier;

public class VirtualThreadUnsafe {
    static {
        try {
            fetchUnsafeHandler();
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }


    public static MethodHandles.Lookup IMPL_LOOKUP;

    public final static Function<Executor, Thread.Builder.OfVirtual> VIRTUAL_THREAD_BUILDER = fetchVirtualThreadBuilder();

    private final static Supplier<Thread> CARRIERTHREAD_SUPPLIER = carrierThreadSupplier();

    private static void fetchUnsafeHandler() throws Throwable {
        try(Arena arena = Arena.ofConfined()) {
            JNIEnv jniEnv = new JNIEnv(arena);

            try(GlobalRef lookup = jniEnv.GetStaticFieldByName(MethodHandles.Lookup.class.getDeclaredField("IMPL_LOOKUP"));) {
                jniEnv.SetStaticFieldByName(VirtualThreadUnsafe.class.getDeclaredField("IMPL_LOOKUP"), lookup);
            }

        }
    }

    private static Function<Executor, Thread.Builder.OfVirtual> fetchVirtualThreadBuilder() {
        var tmp = Thread.ofVirtual().getClass();
        try {
            MethodHandle builderMethodHandle = IMPL_LOOKUP
                    .in(tmp)
                    .findConstructor(tmp, MethodType.methodType(void.class, Executor.class));
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
            MethodHandle currentCarrierThreadMh = IMPL_LOOKUP
                    .in(Thread.class)
                    .findStatic(Thread.class, "currentCarrierThread", MethodType.methodType(Thread.class));

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
