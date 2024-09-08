import io.github.dreamlike.unsafe.vthread.CarrierThreadLocal;
import io.github.dreamlike.unsafe.vthread.Continuation;
import io.github.dreamlike.unsafe.vthread.TerminatingThreadLocal;
import io.github.dreamlike.unsafe.vthread.VirtualThreadUnsafe;
import org.junit.Assert;
import org.junit.Test;

import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
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

    @Test
    public void testCarrierThreadLocal() throws ExecutionException, InterruptedException {
        CarrierThreadLocal<UUID> carrierThreadLocal = new CarrierThreadLocal<>();
        UUID uuid = UUID.randomUUID();
        try (ExecutorService dispatcher = Executors.newSingleThreadExecutor()) {
            dispatcher.submit(() -> {
                carrierThreadLocal.set(uuid);
            }).get();
            dispatcher.submit(() -> {
                Assert.assertEquals(uuid, carrierThreadLocal.get());
            }).get();
            Thread.Builder.OfVirtual virtualBuilder = VirtualThreadUnsafe.VIRTUAL_THREAD_BUILDER.apply(dispatcher);
            virtualBuilder.start(() -> {
                Assert.assertTrue(Thread.currentThread().isVirtual());
                Assert.assertEquals(uuid, carrierThreadLocal.get());
            });
            dispatcher.shutdown();
        }
    }

    @Test
    public void testTerminatingThreadLocal() throws ExecutionException, InterruptedException {
        AtomicBoolean atomicBoolean = new AtomicBoolean(false);
        TerminatingThreadLocal<Object> threadLocal = new TerminatingThreadLocal() {
            @Override
            protected void threadTerminated(Object value) {
                atomicBoolean.set(true);
            }
        };

        Thread thread = new Thread(() -> {
            threadLocal.set(UUID.randomUUID());
        }, "TerminatingThread-0");
        thread.start();
        thread.join();

        Assert.assertSame(thread.getState(), Thread.State.TERMINATED);
        Assert.assertTrue(atomicBoolean.get());
    }
}
