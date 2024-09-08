import io.github.dreamlike.unsafe.vthread.*;
import org.junit.Assert;
import org.junit.Test;

import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class VTTest {


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

    @Test
    public void testPoll() throws Throwable {
        int[] pipeFd = new int[2];
        MethodHandle pipeMH = Linker.nativeLinker()
                .downcallHandle(
                        Linker.nativeLinker().defaultLookup().find("pipe").get(),
                        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS),
                        Linker.Option.critical(true)
                );
        //read
        MethodHandle readMh = Linker.nativeLinker()
                .downcallHandle(
                        Linker.nativeLinker().defaultLookup().find("read").get(),
                        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT),
                        Linker.Option.critical(true)
                );
        //write
        MethodHandle writeMh = Linker.nativeLinker()
                .downcallHandle(
                        Linker.nativeLinker().defaultLookup().find("write").get(),
                        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT),
                        Linker.Option.critical(true)
                );
        int res = (int) pipeMH.invokeExact(MemorySegment.ofArray(pipeFd));
        Assert.assertEquals(0, res);
        int readFd = pipeFd[0];
        int writeFd = pipeFd[1];
        var message = "hello world".getBytes();
        Thread readThread = Thread.startVirtualThread(() -> {
            Poller.poll(readFd, Poller.POLLIN, -1, () -> true);
            byte[] bytes = new byte[message.length];
            try {
                int read = (int) readMh.invokeExact(readFd, MemorySegment.ofArray(bytes), message.length);
                Assert.assertEquals(message.length, read);
                Assert.assertArrayEquals(message, bytes);
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        });

        //睡眠500ms 等待 readThread 线程 到poll
       Thread.sleep(500);

       Assert.assertEquals(readThread.getState(), Thread.State.WAITING);
       res = (int) writeMh.invokeExact(writeFd, MemorySegment.ofArray(message), message.length);
       Assert.assertEquals(message.length, res);
       Thread.sleep(500);
       Assert.assertFalse(readThread.isAlive());
    }
}
