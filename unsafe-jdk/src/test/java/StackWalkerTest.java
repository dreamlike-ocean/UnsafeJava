import org.junit.Assert;
import org.junit.Test;
import top.dreamlike.unsafe.stackWalker.LiveStackFrameInfo;
import top.dreamlike.unsafe.stackWalker.LiveStackWalker;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

public class StackWalkerTest {

    public static final String lock = "这是一个锁";

    @Test
    public void testPlain() {
        StackWalker walker = LiveStackWalker.stackWalker(Set.of());
        List<StackWalker.StackFrame> stackFrames = walkWithMonitor(walker);
        ArrayList<Object> list = new ArrayList<>();
        for (StackWalker.StackFrame frame : stackFrames) {
            Object[] monitors = LiveStackFrameInfo.getMonitors(frame);
            if (monitors.length != 0) {
                list.addAll(Arrays.asList(monitors));
            }
        }
        Assert.assertEquals(1, list.size());
        Assert.assertSame(lock, list.getFirst());

    }

    @Test
    public void testSlot() {
        StackWalker walker = LiveStackWalker.stackWalker(Set.of());

        List<StackWalker.StackFrame> stackFrames = walkWithMonitor(walker);
        StackWalker.StackFrame stackFrame = stackFrames.getFirst();
        Object[] objects = LiveStackFrameInfo.getLocals(stackFrame);
        boolean compiledFrame = LiveStackFrameInfo.isCompiledFrame(stackFrame);
        System.out.println(Arrays.toString(objects) + "是否编译: " + compiledFrame);

        System.out.println(Arrays.toString(LiveStackFrameInfo.getStack(stackFrame)));
    }


    public static List<StackWalker.StackFrame> walkWithMonitor(StackWalker walker) {
        String slot = "这是一个槽";
        synchronized (lock) {
            return walker.walk(stackFrameStream -> stackFrameStream.limit(1).toList());
        }
    }


    public static StackReplaceTestResult walkWithStackReplace(StackWalker walker) {
        MayStackReplace mayStackReplace = new MayStackReplace((int) (Math.random() * 10), (int) (Math.random() * 10));
        int count = mayStackReplace.a() + mayStackReplace.b();
        walker.walk(stackFrameStream -> stackFrameStream.limit(1).toList());
        return new StackReplaceTestResult(count, walker.walk(stackFrameStream -> stackFrameStream.limit(1).toList()));
    }

    record MayStackReplace(int a, int b) {
    }

    record StackReplaceTestResult(int res, List<StackWalker.StackFrame> frames) {}
}
