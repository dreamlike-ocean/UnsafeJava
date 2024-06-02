package top.dreamlike.unsafe.core.panama;

import top.dreamlike.unsafe.core.MasterKey;
import top.dreamlike.unsafe.core.helper.NativeHelper;
import top.dreamlike.unsafe.core.panama.helper.GlobalRef;
import top.dreamlike.unsafe.core.panama.jni.JNIEnv;

import java.lang.foreign.Arena;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class MasterKeyPanamaImpl implements MasterKey {

    public static MasterKeyPanamaImpl INSTANCE = new MasterKeyPanamaImpl();

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

    public MethodHandle openTheDoor(Method method) {
        return NativeHelper.throwable(() -> lookup.unreflect(method));
    }

    public MethodHandle openTheDoor(Constructor ctor) {
        return NativeHelper.throwable(() -> lookup.unreflectConstructor(ctor));
    }

    public VarHandle openTheDoor(Field field) {
        return NativeHelper.throwable(() -> lookup.unreflectVarHandle(field));
    }

    @Override
    public MethodHandles.Lookup getTrustedLookup() {
        return lookup;
    }

}
