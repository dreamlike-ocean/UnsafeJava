import org.junit.Assert;
import org.junit.Test;
import top.dreamlike.unsafe.JNIEnv;
import top.dreamlike.unsafe.helper.GlobalRef;
import top.dreamlike.unsafe.helper.JValue;
import top.dreamlike.unsafe.helper.NativeHelper;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.stream.Collectors;

public class ClassTest {

    private static MethodHandles.Lookup lookup;

    @Test
    public void testFindClass() throws Exception {
        try (Arena arena = Arena.ofConfined();) {
            JNIEnv jniEnv = new JNIEnv(arena);
            try (GlobalRef jclass = new GlobalRef(jniEnv, jniEnv.FindClass(String.class))) {
                MemorySegment jstring = jniEnv.ToString(jclass.ref());
                String s = jniEnv.jstringToCstr(jstring);
                Assert.assertEquals(String.class.toString(), s);
            }
            try (GlobalRef jclass = new GlobalRef(jniEnv, jniEnv.FindClass(JNIEnv.class))) {
                MemorySegment jstring = jniEnv.ToString(jclass.ref());
                String s = jniEnv.jstringToCstr(jstring);
                Assert.assertEquals(JNIEnv.class.toString(), s);
            }
        }

    }

    @Test
    public void getStaticField() throws Throwable {
        try(Arena arena = Arena.ofConfined()) {
            JNIEnv jniEnv = new JNIEnv(arena);
            int defaultInitialCapacity = jniEnv.GetStaticFieldByName(HashMap.class.getDeclaredField("DEFAULT_INITIAL_CAPACITY")).getInt();
            Assert.assertEquals(defaultInitialCapacity, 1 << 4);
            var aShort = jniEnv.GetStaticFieldByName(JNIEnv.class.getDeclaredField("JNI_VERSION")).getInt();
            Assert.assertEquals(aShort, JNIEnv.JNI_VERSION);
        }
    }

    @Test
    public void setStaticField() throws Throwable {
        try(Arena arena = Arena.ofConfined()) {
            JNIEnv jniEnv = new JNIEnv(arena);
            var IMPL_LOOKUP = jniEnv.GetStaticFieldByName(MethodHandles.Lookup.class.getDeclaredField("IMPL_LOOKUP")).toPtr();
            GlobalRef ref = new GlobalRef(jniEnv, IMPL_LOOKUP);
            try(ref) {
                jniEnv.SetStaticFieldByName(ClassTest.class.getDeclaredField("lookup"), new JValue(ref.ref().address()));
            }
            Assert.assertNotNull(lookup);
        }
    }

    @Test
    public void testInvokeStaticMethod() throws Throwable{
        try (Arena arena = Arena.ofConfined();) {
            JNIEnv env = new JNIEnv(arena);


            Method method = Integer.class.getMethod("max", int.class, int.class);
            GlobalRef maxRes = env.CallStaticMethodByName(method, new JValue(2024), new JValue(1));
            Assert.assertEquals(maxRes.jValue.getInt(), 2024);
            method = ClassTest.class.getDeclaredMethod("a", int.class, int.class);

            MemorySegment segment = arena.allocateArray(JValue.jvalueLayout, 2);
            JValue.jintVarhandle.set(segment, 2024);
            JValue.jintVarhandle.set(segment.asSlice(JValue.jvalueLayout.byteSize()), 1);
            maxRes = env.CallStaticMethodByName(method, segment);
            Assert.assertEquals(maxRes.jValue.getInt(), a(2024, 1));

        }
    }

    @Test
    public void newObject() throws Throwable {
        try (Arena arena = Arena.ofConfined();) {
            JNIEnv env = new JNIEnv(arena);
            Constructor<AForTest> constructor = AForTest.class.getConstructor(int.class, int.class);
            GlobalRef ref = env.newObject(constructor, new JValue(1), new JValue(2));
            Object o = env.jObjectToJavaObject(ref.ref());
            Assert.assertTrue(o instanceof AForTest);
            Assert.assertEquals(((AForTest) o).getA(), 3);
        }
    }


    public static int a(int a, int b) {
        return 2024 + a + b;
    }


    public static class AForTest {
        private final int a;

        public AForTest(int a, int b) {
            this.a = a + b;
        }

        public int getA() {
            return a;
        }
    }


}
