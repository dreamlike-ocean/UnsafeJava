package top.dreamlike;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Main {
    public static void main(String[] args) throws NoSuchFieldException, IllegalAccessException {

//        ExecutorService eventLoop = Executors.newSingleThreadExecutor((r) -> new Thread(r,"dreamlike"));
//        CarrierThreadLocal<String> threadLocal = new CarrierThreadLocal<>();
//        Thread.Builder.OfVirtual builder = VirtualThreadUnsafe.VIRTUAL_THREAD_BUILDER
//                .apply(eventLoop);
//        eventLoop.execute(() -> {
//            threadLocal.set("!23123123");
//            System.out.println("dreamlikeThread:"+Thread.currentThread());
//        });
//        builder.start(() -> {
//            System.out.println(Thread.currentThread()+":"+threadLocal.get());
//            System.out.println("virtual thread`s carruer Thread:"+VirtualThreadUnsafe.currentCarrierThread());
//        });
//        eventLoop.close();

        Runnable runnable = () -> {
            Object continuation = Continuation.currentContinuation().internalContinuation;
            System.out.println(System.identityHashCode(continuation));
            System.out.println("first run");
            Continuation.yield();

            continuation = Continuation.currentContinuation().internalContinuation;
            System.out.println(System.identityHashCode(continuation));
            System.out.println("second run");
        };
        Continuation continuation = new Continuation(runnable);

        Object c = continuation.internalContinuation;
        System.out.println(System.identityHashCode(c));

        continuation.run();
        continuation.run();
    }
}