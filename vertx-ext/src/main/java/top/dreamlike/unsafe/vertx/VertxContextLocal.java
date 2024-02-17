package top.dreamlike.unsafe.vertx;

import io.vertx.core.Context;
import io.vertx.core.Vertx;

import java.util.UUID;

public class VertxContextLocal<T> extends ThreadLocal<T> {

    private final String token;

    public VertxContextLocal() {
        this.token = UUID.randomUUID().toString();
    }


    @Override
    public T get() {
        Context context = Vertx.currentContext();
        return context.getLocal(token);
    }

    @Override
    public void set(T value) {
        Context context = Vertx.currentContext();
        context.putLocal(token, value);
    }


}
