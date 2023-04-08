package top.dreamlike;

import io.vertx.core.*;
import io.vertx.core.impl.VertxInternal;

import java.util.UUID;

import static top.dreamlike.AsyncScope.await;

public class Main {
    public static Vertx vertx;
    public static void main(String[] args) throws InterruptedException {
        vertx = Vertx.vertx(new VertxOptions().setEventLoopPoolSize(1));
        VertxContextLocal<UUID> local = new VertxContextLocal<>();

        AsyncScope.open(((VertxInternal) vertx).createEventLoopContext(),() -> {
            UUID uuid = await(longTask(1000));
            local.set(uuid);
            System.out.println(uuid+":await1 end ,thread:"+Thread.currentThread());
            UUID uuid1 = await(longTask(2000));
            System.out.println("await2 end ,thread:"+Thread.currentThread()+":"+local.get());
        });

        vertx.setPeriodic(500, (l) -> {
            System.out.println("Thread:"+Thread.currentThread()+" has running:"+local.get());
        });
    }


    public static Future<UUID> longTask(long duration) {
        Context context = vertx.getOrCreateContext();
        Promise<UUID> promise = Promise.<UUID>promise();
        vertx.setTimer(duration, l -> {
            context.runOnContext(v -> {
                promise.complete(UUID.randomUUID());
            });
        });

        return promise.future();
    }

}