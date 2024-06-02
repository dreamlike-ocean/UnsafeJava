import org.junit.Assert;
import org.junit.Test;
import top.dreamlike.unsafe.thread.ThreadInfo;

import java.util.Arrays;
import java.util.Optional;

public class ThreadInfoTest {


    @Test
    public void testGetAllThread() {
        Thread[] threads = ThreadInfo.getAllThreadInfos();
        Optional<Thread> optional = Arrays.stream(threads)
                .filter(t -> t == Thread.currentThread())
                .findAny();
        Assert.assertTrue(optional.isPresent());

    }
}
