package top.dreamlike;

import net.bytebuddy.ByteBuddy;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.matcher.ElementMatchers;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Scanner;

public class Main {
    public static void main(String[] args) throws Throwable {

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


        new Scanner(System.in)
                .nextLine();
    }

    public static class S {
        protected void threadTerminated(String value) {

        }
    }

    public static class Proxy {

        public static <T> void threadTerminated(T value) {
            System.out.println("terminate:"+value);
        }
    }
}