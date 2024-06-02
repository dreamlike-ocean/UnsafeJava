package top.dreamlike.unsafe.thread;

import top.dreamlike.unsafe.core.MasterKey;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

public class ThreadInfo {

    private static final MethodHandle GET_ALL_THREAD_INFOS_MH;

    public static Thread[] getAllThreadInfos() {
        try {
            return (Thread[]) GET_ALL_THREAD_INFOS_MH.invokeExact();
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }


    static {
        MethodHandles.Lookup lookup = MasterKey.INSTANCE.getTrustedLookup();
        try {
            GET_ALL_THREAD_INFOS_MH = lookup.findStatic(Thread.class, "getAllThreads", MethodType.methodType(Thread[].class));
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }
}
