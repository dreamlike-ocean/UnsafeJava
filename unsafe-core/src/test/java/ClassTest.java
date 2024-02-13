import org.junit.Assert;
import org.junit.Test;
import top.dreamlike.unsafe.JNIEnv;
import top.dreamlike.unsafe.helper.GlobalRef;
import top.dreamlike.unsafe.helper.JValue;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandles;
import java.util.HashMap;

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

}
