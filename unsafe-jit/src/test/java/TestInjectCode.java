import io.github.dreamlike.jit.AMD64Injector;
import io.github.dreamlike.jit.Syscall;
import jnr.x86asm.Asm;
import jnr.x86asm.Assembler;
import jnr.x86asm.CPU;
import jnr.x86asm.Immediate;
import org.junit.Assert;
import org.junit.Test;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;

public class TestInjectCode {

    @Test
    public void test() throws NoSuchMethodException {
        Method method = NativeClass.class.getDeclaredMethod("cal");
        Assembler assembler = getAssembler();
        ByteBuffer buffer = ByteBuffer.allocate(assembler.codeSize());
        assembler.relocCode(buffer, 0);
        AMD64Injector.inject
                (method,
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

        Syscall mprotect = AMD64Injector.mprotect(fp);
        int mprotected = mprotect.ret();
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


    @Test
    public void testGetObjectAddress() throws Throwable {

        Assembler assembler = new Assembler(CPU.X86_64);
        assembler.mov(Asm.rax, Asm.rdi);
        assembler.ret();

        MemorySegment fp = Arena.global().allocate(ValueLayout.JAVA_BYTE, assembler.codeSize());
        assembler.relocCode(fp.asByteBuffer(), 0);
        Syscall mprotect = AMD64Injector.mprotect(fp);
        int mprotected = mprotect.ret();
        if (mprotected != 0) {
            throw new RuntimeException("mprotect failed:" + mprotect);
        }

        MethodHandle downcallHandle = Linker.nativeLinker()
                .downcallHandle(
                        fp,
                        FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS),
                        Linker.Option.critical(true)
                );
        int[] ints = new int[10];

        long address = (long) downcallHandle.invokeExact(MemorySegment.ofArray(ints));

        MemorySegment.ofAddress(address)
                .reinterpret(ints.length * 4)
                .set(ValueLayout.JAVA_INT, 0, 20010329);

        Assert.assertEquals(20010329, ints[0]);

    }

    public Assembler getAssembler() {
        Assembler assembler = new Assembler(CPU.X86_64);
        assembler.mov(Asm.rax, Immediate.imm(20010329));
        assembler.ret();
        return assembler;
    }





    public static class NativeClass {

        public static native long cal();
    }

}
