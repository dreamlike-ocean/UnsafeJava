import io.github.dreamlike.jit.AMD64Injector;
import jnr.x86asm.Asm;
import jnr.x86asm.Assembler;
import jnr.x86asm.CPU;
import jnr.x86asm.Immediate;
import org.junit.Assert;
import org.junit.Test;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;

public class TestInjectCode {

    @Test
    public void test() throws NoSuchMethodException {
        Method method = NativeClass.class.getDeclaredMethod("cal");
        Assembler assembler = getAssembler();
        ByteBuffer buffer = ByteBuffer.allocate(assembler.codeSize());
        assembler.relocCode(buffer, 0);
        AMD64Injector.inject(method,
                buffer.array()
        );
        long cal = NativeClass.cal();
        Assert.assertEquals(20010329, cal);
    }

    @Test
    public void testPanama() throws Throwable {
        Assembler assembler = getAssembler();
        MemorySegment fp = Arena.global().allocate(ValueLayout.JAVA_BYTE, assembler.codeSize());
        assembler.relocCode(fp.asByteBuffer(), 0);

        SysCall mprotect = mprotect(fp);
        int mprotected = mprotect.ret;
        if (mprotected != 0) {
            throw new RuntimeException("mprotect failed:" + mprotect);
        }

        MethodHandle handle = Linker.nativeLinker()
                .downcallHandle(
                        FunctionDescriptor.of(ValueLayout.JAVA_LONG)
                );

        long res = (long) handle.invokeExact(fp);
        Assert.assertEquals(20010329, res);
    }

    public Assembler getAssembler() {
        Assembler assembler = new Assembler(CPU.X86_64);
        assembler.mov(Asm.eax, Immediate.imm(20010329));
        assembler.ret();
        return assembler;
    }


    public SysCall mprotect(MemorySegment memorySegment) throws Throwable {
        MemorySegment fp = Linker.nativeLinker().defaultLookup()
                .find("mprotect").get();

        int pageSize = (int) Linker.nativeLinker()
                .downcallHandle(
                        Linker.nativeLinker().defaultLookup().find("getpagesize").get(),
                        FunctionDescriptor.of(ValueLayout.JAVA_INT)
                ).invokeExact();


        long address = memorySegment.address();
        address = address - (address % pageSize);
        memorySegment = MemorySegment.ofAddress(address); //测试使用 大概率不会超过一页

        StructLayout capturedStateLayout = Linker.Option.captureStateLayout();
        VarHandle errnoHandle = capturedStateLayout.varHandle(MemoryLayout.PathElement.groupElement("errno"));
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment capturedState = arena.allocate(capturedStateLayout);

            MethodHandle methodHandle = Linker.nativeLinker()
                    .downcallHandle(
                            fp,
                            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT),
                            Linker.Option.captureCallState("errno")
                    );
            int res = (int) methodHandle.invokeExact(capturedState, memorySegment, pageSize, 7);
            int errno = (int) errnoHandle.get(capturedState, 0);

            return new SysCall(errno, res);
        }
    }

    record SysCall(int error, int ret) {

    }

    public static class NativeClass {

        public static native long cal();
    }

}
