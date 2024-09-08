package io.github.dreamlike.unsafe.vthread;

import top.dreamlike.unsafe.core.MasterKey;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.function.BooleanSupplier;

public class Poller {
    public static final short POLLIN;
    public static final short POLLOUT;
    public static final short POLLERR;
    public static final short POLLHUP;
    public static final short POLLNVAL;
    public static final short POLLCONN;

    private static final MethodHandle POLLER_POLL_MH;


    static {
        try {
            MethodHandles.Lookup lookup = MasterKey.INSTANCE.getTrustedLookup();
            Class<?> sunNetClazz = Class.forName("sun.nio.ch.Net");
            POLLIN = (short)lookup.findStaticVarHandle(sunNetClazz, "POLLIN", short.class).get();
            POLLOUT = (short)lookup.findStaticVarHandle(sunNetClazz, "POLLOUT", short.class).get();
            POLLERR = (short)lookup.findStaticVarHandle(sunNetClazz, "POLLERR", short.class).get();
            POLLHUP = (short)lookup.findStaticVarHandle(sunNetClazz, "POLLHUP", short.class).get();
            POLLNVAL = (short)lookup.findStaticVarHandle(sunNetClazz, "POLLNVAL", short.class).get();
            POLLCONN = (short)lookup.findStaticVarHandle(sunNetClazz, "POLLCONN", short.class).get();
            Class<?> sunNetPoller = Class.forName("sun.nio.ch.Poller");
            POLLER_POLL_MH = lookup.findStatic(sunNetPoller, "poll", MethodType.methodType(void.class, int.class, int.class, long.class, BooleanSupplier.class));
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public static void poll(int fdVal, int event, long nanos, BooleanSupplier isOpenSupplier) {
        try {
            POLLER_POLL_MH.invokeExact(fdVal, event, nanos, isOpenSupplier);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }
}
