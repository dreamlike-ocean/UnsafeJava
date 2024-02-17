package top.dreamlike.unsafe.core.unreflection;

import top.dreamlike.unsafe.core.helper.GlobalRef;
import top.dreamlike.unsafe.core.helper.NativeHelper;
import top.dreamlike.unsafe.core.jni.JNIEnv;

import java.lang.foreign.Arena;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static top.dreamlike.unsafe.core.helper.NativeHelper.throwable;

public class MasterKey {
    public static MethodHandles.Lookup lookup;
    static {
        try (Arena arena = Arena.ofConfined()) {
            JNIEnv jniEnv = new JNIEnv(arena);
            GlobalRef implLookup = jniEnv.GetStaticFieldByName(MethodHandles.Lookup.class.getDeclaredField("IMPL_LOOKUP"));
            Object o = jniEnv.jObjectToJavaObject(implLookup.ref());
            lookup = (MethodHandles.Lookup) o;
        }catch (Throwable e){
            throw new RuntimeException(e);
        }
    }

    public static MethodHandle openTheDoor(Method method) {
        return NativeHelper.throwable(() -> lookup.unreflect(method));
    }

    public static MethodHandle openTheDoor(Constructor ctor) {
        return NativeHelper.throwable(() -> lookup.unreflectConstructor(ctor));
    }

    public static VarHandle openTheDoor(Field field) {
        return NativeHelper.throwable(() -> lookup.unreflectVarHandle(field));
    }

}
