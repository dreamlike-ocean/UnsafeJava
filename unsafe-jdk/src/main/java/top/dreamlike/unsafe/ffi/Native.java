package top.dreamlike.unsafe.ffi;

import top.dreamlike.unsafe.core.MasterKey;

import java.io.FileDescriptor;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;
import java.util.concurrent.atomic.AtomicLong;

public class Native {
    private static final MethodHandle FIND_NATIVE_MH;

    private static final VarHandle FD_VH;

    private static final VarHandle HANDLE_VH;

    private static final VarHandle MAX_DIRECT_MEMORY_LIMIT_VH;

    private static final VarHandle RESERVED_MEMORY_VH;

    private static final MethodHandle PAGE_SIZE_MH;

    public static final boolean isWindows;

    public static final CABI currentCABI;

    public static long findNative(ClassLoader classLoader, String name) {
        try {
            return (long) FIND_NATIVE_MH.invokeExact(classLoader, name);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    public static long getHandle(FileDescriptor fd) {
        return (long) HANDLE_VH.get(fd);
    }

    public static void setHandle(FileDescriptor fd, long handle) {
        HANDLE_VH.set(fd, handle);
    }


    public static int getFd(FileDescriptor fd) {
        return (int) FD_VH.get(fd);
    }

    public static void setFd(FileDescriptor fd, int nativeFd) {
        FD_VH.set(fd, nativeFd);
    }

    public static long getMaxDirectMemory() {
        return (long) MAX_DIRECT_MEMORY_LIMIT_VH.get();
    }

    public static long getReservedMemory() {
        return ((AtomicLong) RESERVED_MEMORY_VH.get()).get();
    }

    public static int pageSize() {
        try {
            return (int) PAGE_SIZE_MH.invokeExact();
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }


    static {
        try {
            MethodHandles.Lookup trustedLookup = MasterKey.INSTANCE.getTrustedLookup();
            FIND_NATIVE_MH = trustedLookup
                    .findStatic(ClassLoader.class, "findNative", MethodType.methodType(long.class, ClassLoader.class, String.class));
            FD_VH = trustedLookup.findVarHandle(FileDescriptor.class, "fd", int.class);
            HANDLE_VH = trustedLookup.findVarHandle(FileDescriptor.class, "handle", long.class);
            Class<?> nioBitClass = trustedLookup.findClass("java.nio.Bits");
            MAX_DIRECT_MEMORY_LIMIT_VH = trustedLookup
                    .findStaticVarHandle(nioBitClass, "MAX_MEMORY", long.class);
            RESERVED_MEMORY_VH = trustedLookup
                    .findStaticVarHandle(nioBitClass,"RESERVED_MEMORY", AtomicLong.class);

            PAGE_SIZE_MH = trustedLookup
                    .findStatic(nioBitClass,"pageSize", MethodType.methodType(int.class));

            Class<?> foreignUtilsClass = trustedLookup.findClass("jdk.internal.foreign.Utils");
            isWindows = (boolean)
                    trustedLookup.findStaticVarHandle(foreignUtilsClass, "IS_WINDOWS", boolean.class).get();

            Class<?> foreignCABIClass = trustedLookup.findClass("jdk.internal.foreign.CABI");
            currentCABI = CABI.valueOf(trustedLookup.findStaticVarHandle(foreignCABIClass, "CURRENT", foreignCABIClass).get().toString());
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }
}
