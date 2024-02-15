package top.dreamlike.unsafe.unreflection;

import top.dreamlike.unsafe.helper.GlobalRef;
import top.dreamlike.unsafe.jni.JNIEnv;

import java.lang.foreign.Arena;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static top.dreamlike.unsafe.helper.NativeHelper.throwable;

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
        return throwable(() -> lookup.unreflect(method));
    }

    public static MethodHandle openTheDoor(Constructor ctor) {
        return throwable(() -> lookup.unreflectConstructor(ctor));
    }

    public static VarHandle openTheDoor(Field field) {
        return throwable(() -> lookup.unreflectVarHandle(field));
    }

}
