package io.github.dreamlike.jit;

import jdk.vm.ci.code.site.DataPatch;
import jdk.vm.ci.code.site.Mark;
import jdk.vm.ci.code.site.Site;
import jdk.vm.ci.hotspot.HotSpotCompiledCode;
import jdk.vm.ci.hotspot.HotSpotCompiledNmethod;
import jdk.vm.ci.hotspot.HotSpotResolvedJavaMethod;
import jdk.vm.ci.meta.Assumptions;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.runtime.JVMCI;
import jdk.vm.ci.runtime.JVMCIBackend;
import jdk.vm.ci.runtime.JVMCICompiler;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

public class AMD64Injector {

    public static final long pageSize;

    private static final MethodHandle mprotectMH;

    static {
        try {
            MethodHandles.lookup().ensureInitialized(Init.class);

            pageSize = (int) Linker.nativeLinker()
                    .downcallHandle(
                            Linker.nativeLinker().defaultLookup().find("getpagesize").get(),
                            FunctionDescriptor.of(ValueLayout.JAVA_INT)
                    ).invokeExact();

            mprotectMH = Linker.nativeLinker()
                    .downcallHandle(
                            Linker.nativeLinker().defaultLookup().find("mprotect").get(),
                            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT),
                            Linker.Option.captureCallState("errno")
                    );
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    private static final JVMCIBackend backend = JVMCI.getRuntime().getHostJVMCIBackend();

    public static int offset(Field field) {
        return backend.getMetaAccess().lookupJavaField(field)
                .getOffset();
    }

    public static void inject(Method m, byte[] machineCode) {
        int modifiers = m.getModifiers();
        if (!Modifier.isNative(modifiers)) {
            throw new IllegalArgumentException("method is not native");
        }

        //0x41, (byte)0x81, (byte)0x7f
        //4字节对齐
        int length = machineCode.length;
        if (length % 4 != 0) {
            length += 4 - length % 4;
        }

        byte[] realMachineCode = new byte[length + 3];
        System.arraycopy(machineCode, 0, realMachineCode, 0, machineCode.length);
        //参考bool NativeNMethodCmpBarrier::check_barrier
        //#ifdef _LP64
        //  enum Intel_specific_constants {
        //    instruction_code        = 0x81,
        //    instruction_size        = 8,
        //    imm_offset              = 4,
        //    instruction_rex_prefix  = Assembler::REX | Assembler::REX_B,
        //    instruction_modrm       = 0x7f  // [r15 + offset]
        //  };
        realMachineCode[length + 0] = 0x41;
        realMachineCode[length + 1] = (byte) 0x81;
        realMachineCode[length + 2] = 0x7f;

        ResolvedJavaMethod resolvedJavaMethod = backend.getMetaAccess().lookupJavaMethod(m);
        HotSpotCompiledNmethod nm = new HotSpotCompiledNmethod(
                m.getName(),
                realMachineCode,
                realMachineCode.length,
                //防止nmethod entry barrier is missing 可
                // 参考 https://github.com/openjdk/jdk/blob/8635642dbdfb74d2ae50a51611fd2c5980fe6e74/src/hotspot/share/jvmci/jvmciCodeInstaller.cpp#L1330的case ENTRY_BARRIER_PATCH
                new Site[]{new Mark(realMachineCode.length - 3, 7 /*jdk.graal.compiler.hotspot.HotSpotMarkId.ENTRY_BARRIER_PATCH*/)},
                new Assumptions.Assumption[0],
                new ResolvedJavaMethod[0],
                new HotSpotCompiledCode.Comment[0],
                new byte[0],
                1,
                new DataPatch[0],
                true,
                0,
                null,
                (HotSpotResolvedJavaMethod) resolvedJavaMethod,
                JVMCICompiler.INVOCATION_ENTRY_BCI,
                1,
                0,
                false
        );
        backend.getCodeCache().setDefaultCode(resolvedJavaMethod, nm);


    }

    public static Syscall mprotect(MemorySegment memorySegment) throws Throwable {
        long pageStart = memorySegment.address();
        pageStart = pageStart - (pageStart % pageSize);
        memorySegment = MemorySegment.ofAddress(pageStart); //测试使用 大概率不会超过一页

        long pageEnd = memorySegment.address() + memorySegment.byteSize();
        pageEnd = pageEnd - (pageStart % pageSize);

        StructLayout capturedStateLayout = Linker.Option.captureStateLayout();
        VarHandle errnoHandle = capturedStateLayout.varHandle(MemoryLayout.PathElement.groupElement("errno"));
        //计算该区间需要多少页
        long count = (pageEnd - pageStart) / pageSize + 1;
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment capturedState = arena.allocate(capturedStateLayout);
            int res = (int) mprotectMH.invokeExact(capturedState, memorySegment, (int) (count * pageSize), 7);
            int errno = (int) errnoHandle.get(capturedState, 0);
            return new Syscall(errno, res);
        }
    }
}
