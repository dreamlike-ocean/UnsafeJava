import org.junit.Assert;
import org.junit.Test;
import top.dreamlike.unsafe.core.panama.jni.JNIEnv;
import top.dreamlike.unsafe.core.panama.jni.RegisterNative;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.List;

public class JniTest {

    @Test
    public void test() throws Throwable {
        MethodHandle method = MethodHandles.lookup()
                .findStatic(Math.class, "addExact", MethodType.methodType(int.class, int.class, int.class));

        //downcall 拿到对应的函数调用方法
        //downcall mh 添加参数 jnienv* jclass -> mh1
        // mh1 -> fp (upcall )
        MethodHandle getpagesizeMH = Linker.nativeLinker()
                .downcallHandle(
                        Linker.nativeLinker().defaultLookup().find("getpagesize").get(),
                        FunctionDescriptor.of(ValueLayout.JAVA_INT),
                        Linker.Option.critical(true)
                );
        RegisterNative.nativeBinder(
                new JNIEnv(Arena.global()),
                NativeHolder.class,
                List.of(
                        new RegisterNative.MethodBinderRequest(
                                NativeHolder.class.getDeclaredMethod("add", int.class, int.class),
                                method
                        ),
                        new RegisterNative.MethodBinderRequest(
                                NativeHolder.class.getDeclaredMethod("pageSize"),
                                getpagesizeMH
                        )
                )
        );

        int res = NativeHolder.add(1, 2);
        Assert.assertEquals(3, res);

        Assert.assertEquals((int)getpagesizeMH.invokeExact(), NativeHolder.pageSize());
    }

    static class NativeHolder {
        static native int add(int a, int b);

        static native int pageSize();
    }
}
