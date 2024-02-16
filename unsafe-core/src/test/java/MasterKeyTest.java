import org.junit.Assert;
import org.junit.Test;
import top.dreamlike.unsafe.unreflection.MasterKey;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.UUID;

public class MasterKeyTest {

    @Test
    public void openPrivateMethod() throws Throwable {
        Method privateMethod = PrivateClass.class.getDeclaredMethod("privateMethod", String.class);

        Assert.assertThrows(IllegalAccessException.class, () -> {
            privateMethod.invoke(null, "test");
        });

        String arg0 = UUID.randomUUID().toString();
        String res = (String) MasterKey.openTheDoor(privateMethod).invokeExact(arg0);
        Assert.assertEquals(arg0, res);

    }

    @Test
    public void openPrivateField() throws Throwable {
        Field field = PrivateClass.class.getDeclaredField("uuid");

        Assert.assertThrows(IllegalAccessException.class, () -> {
            field.get(null);
        });

        UUID res = (UUID) MasterKey.openTheDoor(field).get();
        Assert.assertEquals(PrivateClass.getUuid(), res);

    }

    @Test
    public void openPrivateMethodInJdk() throws Throwable {
        Method currentCarrierThread = Thread.class.getDeclaredMethod("currentCarrierThread");
        Assert.assertThrows(IllegalAccessException.class, () -> {
            currentCarrierThread.invoke(null);
        });

        MethodHandle currentCarrierThreadMh = MasterKey.openTheDoor(currentCarrierThread);
        Thread thread = (Thread) currentCarrierThreadMh.invokeExact();
        Assert.assertEquals(thread, Thread.currentThread());

    }





}
