package top.dreamlike.unsafe.stackWalker;

import top.dreamlike.unsafe.core.MasterKey;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;

public class LiveStackFrameInfo {

    private static final MethodHandle GET_MONITOR_MH;

    private static final MethodHandle GET_LOCALS_MH;

    private static final MethodHandle GET_STACK_MH;

    private static final VarHandle MOD;

    public static final int MODE_INTERPRETED;

    public static final int MODE_COMPILED;


    public static Object[] getLocals(StackWalker.StackFrame liveStackFrameInfo) {
        try {
            return (Object[]) GET_LOCALS_MH.invokeExact(liveStackFrameInfo);
        } catch (Throwable throwable) {
            throw new RuntimeException(throwable);
        }
    }

    public static Object[] getStack(StackWalker.StackFrame liveStackFrameInfo) {
        try {
            return (Object[]) GET_STACK_MH.invokeExact(liveStackFrameInfo);
        } catch (Throwable throwable) {
            throw new RuntimeException(throwable);
        }
    }

    public static Object[] getMonitors(StackWalker.StackFrame liveStackFrameInfo) {
        try {
            return (Object[]) GET_MONITOR_MH.invokeExact(liveStackFrameInfo);
        } catch (Throwable throwable) {
            throw new RuntimeException(throwable);
        }
    }

    public static int getMode(StackWalker.StackFrame liveStackFrameInfo) {
        return (int) MOD.get(liveStackFrameInfo);
    }

    public static boolean isCompiledFrame(StackWalker.StackFrame liveStackFrameInfo) {
        return getMode(liveStackFrameInfo) == MODE_COMPILED;
    }

    static {
        MethodHandles.Lookup lookup = MasterKey.INSTANCE.getTrustedLookup();
        try {
            Class<?> jdkLiveStackFrameClass = lookup.findClass("java.lang.LiveStackFrameInfo");
            MODE_COMPILED = ((int) lookup.findStaticVarHandle(jdkLiveStackFrameClass, "MODE_COMPILED", int.class).get());
            MODE_INTERPRETED = ((int) lookup.findStaticVarHandle(jdkLiveStackFrameClass, "MODE_INTERPRETED", int.class).get());
            MOD = lookup.findVarHandle(jdkLiveStackFrameClass, "mode", int.class);
            GET_LOCALS_MH = lookup.findVirtual(jdkLiveStackFrameClass, "getLocals", MethodType.methodType(Object[].class))
                    .asType(MethodType.methodType(Object[].class, StackWalker.StackFrame.class));
            GET_STACK_MH = lookup.findVirtual(jdkLiveStackFrameClass, "getStack", MethodType.methodType(Object[].class))
                    .asType(MethodType.methodType(Object[].class, StackWalker.StackFrame.class));
            GET_MONITOR_MH = lookup.findVirtual(jdkLiveStackFrameClass, "getMonitors", MethodType.methodType(Object[].class))
                    .asType(MethodType.methodType(Object[].class, StackWalker.StackFrame.class));
        } catch (Throwable throwable) {
            throw new RuntimeException(throwable);
        }
    }
}
