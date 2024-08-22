import io.github.dreamlike.jit.AMD64Injector;
import io.github.dreamlike.jit.Syscall;
import jnr.x86asm.Asm;
import jnr.x86asm.Assembler;
import jnr.x86asm.CPU;
import jnr.x86asm.Immediate;
import org.junit.Assert;
import org.junit.Test;
import org.openjdk.jol.vm.VM;

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
    public void testGetAddress() throws Throwable {
        Assembler assembler = new Assembler(CPU.X86_64);
//        https://stackoverflow.com/questions/41693637/whats-the-calling-convention-for-the-java-code-in-linux-platform
        //x86 systemV java的调用约定里面 编译帧是的调用约定跟当前平台的调用约定向右移动一位
        assembler.mov(Asm.rax, Asm.rsi);
        assembler.ret();

        Method method = NativeClass.class.getDeclaredMethod("getAddress", Object.class);
        ByteBuffer buffer = ByteBuffer.allocate(assembler.codeSize());
        assembler.relocCode(buffer, 0);
        AMD64Injector.inject
                (
                        method,
                        buffer.array()
                );

        SimpleBean simpleBean = new SimpleBean();
        long address = NativeClass.getAddress(simpleBean);
        Assert.assertEquals(VM.current().addressOf(simpleBean),address);


        var code = new byte[] {
                (byte)0x48, (byte)0x63, (byte)0xd2, // movslq  %edx, %rdx
                (byte)0x48, (byte)0x89, (byte)0x0c, (byte)0x32, //   movq    %rcx, (%rdx,%rsi)
                (byte)0xc3 //        ret
        };
        Method setLongValueMethod = NativeClass.class.getDeclaredMethod("setLongValue", Object.class, int.class, long.class);
        AMD64Injector.inject(setLongValueMethod, code);

        int offset = AMD64Injector.offset(SimpleBean.class.getDeclaredField("a"));

        Assert.assertEquals(0, simpleBean.getA());

        NativeClass.setLongValue(simpleBean, offset, 20010329);
        Assert.assertEquals(20010329, simpleBean.getA());

        simpleBean.setA(1000L);

        code = new byte[] {
                (byte)0x48, (byte)0x63, (byte)0xd2, // movslq  %edx, %rdx
                (byte)0x48, (byte)0x8b, (byte)0x04, (byte)0x32, //   mov    (%rdx,%rsi,1),%rax
                (byte)0xc3 //        ret
        };
        Method getLongValueMethod = NativeClass.class.getDeclaredMethod("getLongValue", Object.class, int.class);
        AMD64Injector.inject(getLongValueMethod, code);
        Assert.assertEquals(1000, NativeClass.getLongValue(simpleBean, offset));
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

        public static native long getAddress(Object o);

        public static native void setLongValue(Object o, int offset, long value);

        public static native long getLongValue(Object o, int offset);
    }

}
