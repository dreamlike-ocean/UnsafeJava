import org.junit.Assert;
import org.junit.Test;
import top.dreamlike.unsafe.ffi.CABI;
import top.dreamlike.unsafe.ffi.Native;

import java.io.FileDescriptor;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.ByteBuffer;

public class NativeTest {

    @Test
    public void testFd() {
        FileDescriptor in = FileDescriptor.out;
        int fd = Native.getFd(in);
        Assert.assertEquals(1, fd);
        FileDescriptor descriptor = new FileDescriptor();
        Native.setFd(descriptor, 13);
        Assert.assertEquals(13, Native.getFd(descriptor));

    }

    @Test
    public void testMaxDirectMemory() throws Throwable {
        long maxDirectMemory = Native.getMaxDirectMemory();
        Assert.assertTrue(maxDirectMemory > 0);
        ByteBuffer byteBuffer = ByteBuffer.allocateDirect(1024);
        long reservedMemory = Native.getReservedMemory();
        Assert.assertTrue(reservedMemory > 0);

        MemorySegment pageSizeFp = Linker.nativeLinker()
                .defaultLookup()
                .find("getpagesize")
                .get();

        MethodHandle nativePageSizeMH = Linker.nativeLinker()
                .downcallHandle(pageSizeFp, FunctionDescriptor.of(ValueLayout.JAVA_INT));
        int nativePageSize = (int) nativePageSizeMH.invokeExact();
        Assert.assertEquals(nativePageSize, Native.pageSize());

    }

    @Test
    public void testCABI() {
        CABI cabi = Native.currentCABI;
        System.out.println(cabi);
        Assert.assertNotNull(cabi);
    }


}
