package top.dreamlike.unsafe.core.unsafe;

import sun.misc.Unsafe;
import top.dreamlike.unsafe.core.MasterKey;
import top.dreamlike.unsafe.core.helper.NativeHelper;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class MasterKeyUnsafeImpl implements MasterKey {

    public static final MasterKeyUnsafeImpl INSTANCE = new MasterKeyUnsafeImpl();

    public static MethodHandles.Lookup lookup;
    static {
       try {
           Field field = Unsafe.class.getDeclaredField("theUnsafe");
           field.setAccessible(true);
           Unsafe unsafe = (Unsafe) field.get(null);
           Field implLookupField = MethodHandles.Lookup.class.getDeclaredField("IMPL_LOOKUP");
           Object base = unsafe.staticFieldBase(implLookupField);
           long fieldOffset = unsafe.staticFieldOffset(implLookupField);
           lookup = ((MethodHandles.Lookup) unsafe.getObject(base, fieldOffset));
           System.out.println("unsafe impl");
       }catch (Exception e) {
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
