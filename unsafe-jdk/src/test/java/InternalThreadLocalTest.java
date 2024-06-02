import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import top.dreamlike.unsafe.vthread.CarrierThreadLocal;
import top.dreamlike.unsafe.vthread.TerminatingThreadLocal;
import top.dreamlike.unsafe.vthread.VirtualThreadUnsafe;

import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

public class InternalThreadLocalTest {

    static ExecutorService executor;

    @BeforeClass
    public static void init() {
        executor = Executors.newSingleThreadExecutor();
    }

    @AfterClass
    public static void destroy() {
        executor.close();
    }

    @Test
    public void testCarrierThreadLocal() throws InterruptedException {
        CarrierThreadLocal<String> threadLocal = new CarrierThreadLocal<>();
        Thread.Builder.OfVirtual virtual = VirtualThreadUnsafe.VIRTUAL_THREAD_BUILDER
                .apply(executor);
        AtomicReference<Thread> threadReference = new AtomicReference<>();
        AtomicReference<String> randomReference = new AtomicReference<>();
        executor.submit(() -> {
            threadReference.set(Thread.currentThread());
            String value = UUID.randomUUID().toString();
            threadLocal.set(value);
            randomReference.set(value);
        });

        virtual.start(() -> {
            Thread currentCarrierThread = VirtualThreadUnsafe.currentCarrierThread();
            Assert.assertEquals(currentCarrierThread, threadReference.get());
            Assert.assertNotNull(threadLocal.get());
            Assert.assertEquals(randomReference.get(), threadLocal.get());
        }).join();
        executor.submit(threadLocal::remove);

        virtual.start(() -> {
            Assert.assertNull(threadLocal.get());
        }).join();
    }

    @Test
    public void testTerminatingThreadLocal() throws InterruptedException {
        String random = UUID.randomUUID().toString();
        TerminatingThreadLocal<String> threadLocal = new TerminatingThreadLocal<>() {
            @Override
            protected void threadTerminated(String value) {
               Assert.assertEquals(value, random);
            }
        };
        Thread thread = new Thread(() -> {
            threadLocal.set(random);
        });
        thread.start();
        thread.join();
    }
}
