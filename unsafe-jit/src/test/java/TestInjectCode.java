import io.github.dreamlike.jit.AMD64Injector;
import jnr.x86asm.Asm;
import jnr.x86asm.Assembler;
import jnr.x86asm.CPU;
import jnr.x86asm.Immediate;
import org.junit.Assert;
import org.junit.Test;

import java.lang.reflect.Method;
import java.nio.ByteBuffer;

public class TestInjectCode {

    @Test
    public void test() throws NoSuchMethodException {
        Method method = NativeClass.class.getDeclaredMethod("cal");
        Assembler assembler = new Assembler(CPU.X86_64);
        assembler.mov(Asm.eax, Immediate.imm(20010329));
        assembler.ret();
        ByteBuffer buffer = ByteBuffer.allocate(assembler.codeSize());
        assembler.relocCode(buffer, 0);
        AMD64Injector.inject(method,
                buffer.array()
        );
        long cal = NativeClass.cal();
        Assert.assertEquals(20010329,cal);
    }

    public static class NativeClass {

        public static native long cal();
    }

}
