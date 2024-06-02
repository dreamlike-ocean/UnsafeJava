package top.dreamlike.unsafe.core;

import top.dreamlike.unsafe.core.panama.MasterKeyPanamaImpl;
import top.dreamlike.unsafe.core.unsafe.MasterKeyUnsafeImpl;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

public interface MasterKey {

    MasterKey INSTANCE = Runtime.version().feature() >= 22 ? new MasterKeyPanamaImpl() : new MasterKeyUnsafeImpl();

    MethodHandle openTheDoor(Method method);

    MethodHandle openTheDoor(Constructor ctor);

    VarHandle openTheDoor(Field field);

    MethodHandles.Lookup getTrustedLookup();

    static boolean isPanamaBackend() {
        return INSTANCE instanceof MasterKeyPanamaImpl;
    }
}
