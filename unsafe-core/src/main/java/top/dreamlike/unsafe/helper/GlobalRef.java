package top.dreamlike.unsafe.helper;

import top.dreamlike.unsafe.JNIEnv;

import java.lang.foreign.MemorySegment;

public class GlobalRef implements AutoCloseable {

    private final MemorySegment globalRef;
    private final JNIEnv env;

    public GlobalRef(JNIEnv env, MemorySegment jobject) {
        this.env = env;
        globalRef = env.NewGlobalRef(jobject);
    }

    public MemorySegment ref() {
        return globalRef;
    }

    @Override
    public void close() throws Exception {
        env.DeleteGlobalRef(globalRef);
    }
}
