package io.github.dreamlike.unsafe.vthread;


import top.dreamlike.unsafe.core.MasterKey;

import java.lang.invoke.LambdaMetafactory;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.util.function.BiFunction;
import java.util.function.Function;

public class Continuation {

    public final Object internalContinuation;

    private static final Class scopeClass = initScopeClass();

    private static final Class continuationClass = initContinuationClass();
    private static final Object scope = initContinuationScope();
    private static final String SCOPE_NAME = "UNSAFE_CONTINUATION_SCOPE";

    private static final BiFunction<Object, Runnable, Object> CONTINUATION_CONSTRUCTOR = initContinuationConstructor();

    private static final Function<Object, Object> CURRENT_CONTINUATION_GETTER = initCurrentContinuationSupplier();

    private static final MethodHandle CONTINUATION_YIELD_MH = initYieldMH();

    private static final MethodHandle CONTINUATION_RUN_MH = initRunMH();

    private static final MethodHandle CONTINUATION_IS_DONE_MH = initIsDoneMH();



    public Continuation(Runnable runnable) {
        this(CONTINUATION_CONSTRUCTOR.apply(scope, runnable));
    }

    private Continuation(Object internalContinuation) {
        this.internalContinuation = internalContinuation;
    }

    public boolean inContinuation() {
        return internalContinuation != null;
    }


    public void run() {
        try {
            CONTINUATION_RUN_MH.invokeExact(internalContinuation);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public boolean isDone() {
        try {
            return (boolean) CONTINUATION_IS_DONE_MH.invokeExact(internalContinuation);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public static boolean yield() {
        try {
            return (boolean) CONTINUATION_YIELD_MH.invokeExact(scope);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public static Continuation currentContinuation() {
        return new Continuation(CURRENT_CONTINUATION_GETTER.apply(scope));
    }

    private static MethodHandle initRunMH() {
        try {
            MethodHandle methodHandle = MasterKey.INSTANCE.openTheDoor(continuationClass.getDeclaredMethod("run"));
            return methodHandle
                    .asType(MethodType.methodType(void.class, Object.class));
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    private static MethodHandle initYieldMH() {
        try {
            MethodHandle methodHandle = MasterKey.INSTANCE.openTheDoor(continuationClass.getDeclaredMethod("yield", scopeClass));
            return methodHandle
                    .asType(MethodType.methodType(boolean.class, Object.class));
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }

    }

    private static Class initScopeClass() {
        try {
            String scopeClassName = "jdk.internal.vm.ContinuationScope";
            return Class.forName(scopeClassName);
        } catch (Throwable throwable) {
            throw new RuntimeException(throwable);
        }
    }

    private static Object initContinuationScope() {
        try {
            MethodHandle scopeConstructor = MasterKey.INSTANCE.openTheDoor(scopeClass.getDeclaredConstructor(String.class));
            scopeConstructor = scopeConstructor
                    .asType(scopeConstructor.type().changeReturnType(Object.class));
            return scopeConstructor.invokeExact(SCOPE_NAME);
        } catch (Throwable throwable) {
            throw new RuntimeException(throwable);
        }

    }

    private static Class initContinuationClass() {
        try {
            String scopeClassName = "jdk.internal.vm.Continuation";
            return Class.forName(scopeClassName);
        } catch (Throwable throwable) {
            throw new RuntimeException(throwable);
        }
    }

    private static BiFunction<Object, Runnable, Object> initContinuationConstructor() {
        try {
            String continuationClassName = "jdk.internal.vm.Continuation";
            Class<?> continuationClass = Class.forName(continuationClassName);
            MethodHandle continuationConstructor = MasterKey.INSTANCE.openTheDoor(continuationClass.getDeclaredConstructor(scopeClass, Runnable.class));

            MethodHandle methodHandle = LambdaMetafactory.metafactory(
                    VirtualThreadUnsafe.IMPL_LOOKUP,
                    "apply",
                    MethodType.methodType(BiFunction.class),
                    MethodType.methodType(Object.class, Object.class, Object.class),
                    continuationConstructor,
                    continuationConstructor.type()
            ).getTarget();
            return (BiFunction<Object, Runnable, Object>) methodHandle.invoke();
        } catch (Throwable throwable) {
            throw new RuntimeException(throwable);
        }
    }

    //通过ContinuationScope来获取Continuation
    private static Function<Object, Object> initCurrentContinuationSupplier() {
        try {
            MethodHandle getCurrentContinuation = MasterKey.INSTANCE.openTheDoor(continuationClass.getDeclaredMethod("getCurrentContinuation", scopeClass));
            MethodHandle lambda = LambdaMetafactory.metafactory(
                    VirtualThreadUnsafe.IMPL_LOOKUP,
                    "apply",
                    MethodType.methodType(Function.class),
                    MethodType.methodType(Object.class, Object.class),
                    getCurrentContinuation,
                    getCurrentContinuation.type()
            ).getTarget();
            return (Function<Object, Object>) lambda.invoke();
        } catch (Throwable throwable) {
            throw new RuntimeException(throwable);
        }
    }

    private static MethodHandle initIsDoneMH() {
        try {
            MethodHandle handle = MasterKey.INSTANCE.openTheDoor(continuationClass.getDeclaredMethod("isDone"));
            return handle
                    .asType(MethodType.methodType(boolean.class, Object.class));
        }catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }
}
