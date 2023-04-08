package top.dreamlike;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Vertx;

public class AsyncScope {


    public static void open(Context context, Runnable runnable){
       context.runOnContext(v -> {
           new Continuation(runnable)
                   .run();
       });
    }

    public static void open(Runnable runnable){
        Context context = Vertx.currentContext();
        if (context == null) {
            throw new IllegalArgumentException("should in vertx context");
        }
        context.runOnContext(v -> {
            new Continuation(runnable)
                    .run();
        });
    }

    public static <T> T await(Future<T> future) {
        Continuation currentContinuation = Continuation.currentContinuation();
        if (!currentContinuation.inContinuation()) {
            throw new IllegalArgumentException("AsyncScope dont activate!");
        }
        if (future.isComplete()){
            return future.result();
        }
        future.onComplete(ar -> currentContinuation.run());
        Continuation.yield();
        if (future.succeeded()) {
            return future.result();
        }
        throw new RuntimeException(future.cause());
    }
}
