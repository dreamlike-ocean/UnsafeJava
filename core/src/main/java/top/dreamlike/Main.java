package top.dreamlike;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandles;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Main {

    private static MethodHandles.Lookup a = MethodHandles.lookup();
    public static void main(String[] args) throws Throwable {
//
//        ExecutorService eventLoop = Executors.newSingleThreadExecutor((r) -> new Thread(r,"dreamlike"));
//        CarrierThreadLocal<String> threadLocal = new CarrierThreadLocal<>();
//        Thread.Builder.OfVirtual builder = VirtualThreadUnsafe.VIRTUAL_THREAD_BUILDER
//                .apply(eventLoop);
//        eventLoop.execute(() -> {
//            threadLocal.set("!23123123");
//            System.out.println("dreamlikeThread:"+Thread.currentThread());
//        });
//
//        builder.start(() -> {
//            System.out.println(Thread.currentThread()+":"+threadLocal.get());
//            System.out.println("virtual thread`s carruer Thread:"+VirtualThreadUnsafe.currentCarrierThread());
//        });
//        eventLoop.close();
//
//        Runnable runnable = () -> {
//            Object continuation = Continuation.currentContinuation().internalContinuation;
//            System.out.println(System.identityHashCode(continuation));
//            System.out.println("first run");
//            Continuation.yield();
//
//            continuation = Continuation.currentContinuation().internalContinuation;
//            System.out.println(System.identityHashCode(continuation));
//            System.out.println("second run");
//        };
//        Continuation continuation = new Continuation(runnable);
//
//        Object c = continuation.internalContinuation;
//        System.out.println(System.identityHashCode(c));
//
//        continuation.run();
//        continuation.run();

//        try (Arena arena = Arena.ofConfined()) {
//            MemorySegment segment = arena.allocate(1024);
//
//        }


    }


}