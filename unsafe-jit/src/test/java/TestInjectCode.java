import io.github.dreamlike.jit.AMD64Injector;
import org.junit.Assert;
import org.junit.Test;

import java.lang.reflect.Method;

public class TestInjectCode {

    @Test
    public void test() throws NoSuchMethodException {
        Method method = NativeClass.class.getDeclaredMethod("cal");
//        mov     eax, 20010329
        //ret
        AMD64Injector.inject(method, new byte[]{
                        (byte) 0xb8, (byte) 0x59, 0x55, 0x31, 0x01,
                        (byte) 0xc3
                }
        );
        long cal = NativeClass.cal();
        Assert.assertEquals(20010329,cal);
    }

    public static class NativeClass {

        public static native long cal();
    }

}
