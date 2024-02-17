import org.junit.Assert;
import org.junit.Test;
import top.dreamlike.unsafe.vthread.Continuation;

import java.util.concurrent.atomic.AtomicInteger;

public class ContinuationTest {


    @Test
    public void testContinuation() {
        AtomicInteger count = new AtomicInteger(0);
        Continuation continuation = new Continuation(() -> {
            count.incrementAndGet();
            Assert.assertNotNull(Continuation.currentContinuation());
            Continuation.yield();
            count.incrementAndGet();
        });

        continuation.run();
        Assert.assertEquals(count.get(), 1);
        Assert.assertFalse(continuation.isDone());
        continuation.run();
        Assert.assertEquals(count.get(), 2);
        Assert.assertTrue(continuation.isDone());
    }
}
