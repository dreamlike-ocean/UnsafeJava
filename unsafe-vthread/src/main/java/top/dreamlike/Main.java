package top.dreamlike;

import java.lang.invoke.MethodHandles;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static java.lang.StringTemplate.STR;

public class Main {

    private static long a = 20210214;

    public static MethodHandles.Lookup IMPL_LOOKUP;

    public static void main(String[] args) throws Throwable {
        ExecutorService service = Executors.newSingleThreadExecutor(r ->
                new Thread(r, "dreamlike-jni-hack-VirtualThread")
        );
        Thread.Builder.OfVirtual virtual = VirtualThreadUnsafe.VIRTUAL_THREAD_BUILDER.apply(service);
        CarrierThreadLocal<String> local = new CarrierThreadLocal<>();
        service.submit(() -> {
            local.set("hello vthread");
            virtual.start(() -> {
                System.out.println(STR."\{local.get()} \{Thread.currentThread()}");
            });
        });

    }

}