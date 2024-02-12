package top.dreamlike.unsafe;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;

import static java.lang.StringTemplate.STR;
import static top.dreamlike.unsafe.helper.LambdaHelper.throwable;

public class JNIEnv {

    private final static MemorySegment MAIN_VM_Pointer = throwable(JNIEnv::initMainVM);

    private final static long JNI_VERSION = 0x00150000;

    private final static MethodHandle GET_JNIENV_MH = throwable(JNIEnv::initGetJNIEnvMH);

    private final static MethodHandle NewGlobalRef_MH = Linker.nativeLinker().downcallHandle(FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));

    private final static MethodHandle DeleteGlobalRef_MH = Linker.nativeLinker().downcallHandle(FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS));



    private final static MethodHandle NewStringPlatform = throwable(() -> {
        MemorySegment JNU_NewStringPlatformFP = SymbolLookup.loaderLookup()
                .find("JNU_NewStringPlatform")
                .get();
        return Linker.nativeLinker()
                .downcallHandle(FunctionDescriptor.of(
                        /*jstring*/ValueLayout.ADDRESS,
                        /*JNIEnv *env */ValueLayout.ADDRESS,
                        /*const char *str*/ ValueLayout.ADDRESS
                )).bindTo(JNU_NewStringPlatformFP);
    });



    private final Arena arena;
    private final MemorySegment jniEnvPointer;

    private final JNIEnvFunctions functions;


    public JNIEnv(Arena arena) {
        this.arena = arena;
        jniEnvPointer = initJniEnv();
        functions = new JNIEnvFunctions(jniEnvPointer);
    }

    public MemorySegment NewGlobalRef(MemorySegment jobject) {
        try {
            return (MemorySegment) NewGlobalRef_MH.invokeExact(functions.NewGlobalRefFp, jniEnvPointer, jobject);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    public void DeleteGlobalRef(MemorySegment globalRef) {
        try {
            DeleteGlobalRef_MH.invokeExact(functions.DeleteGlobalRefFp, jniEnvPointer, globalRef);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    public MemorySegment cstrToJstring(MemorySegment cstr) {
        return throwable(() -> (MemorySegment) NewStringPlatform.invokeExact(jniEnvPointer, cstr));
    }




    private MemorySegment initJniEnv() {
        try {
            return ((MemorySegment) GET_JNIENV_MH.invokeExact(MAIN_VM_Pointer, JNI_VERSION))
                    .reinterpret(Long.MAX_VALUE);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }



    private static MemorySegment initMainVM() throws Throwable {
        Runtime.getRuntime().loadLibrary("java");
        String javaHomePath = System.getProperty("java.home", "");
        if (javaHomePath.isBlank()) {
            throw new RuntimeException("cant find java.home!");
        }
        //根据当前系统判断使用哪个后缀名
        String libName = System.mapLibraryName("jvm");
        String jvmPath = STR."\{javaHomePath}/lib/server/\{libName}";
        Runtime.getRuntime().load(jvmPath);
        MemorySegment jniGetCreatedJavaVM_FP = SymbolLookup.loaderLookup()
                .find("JNI_GetCreatedJavaVMs")
                .get();
        MethodHandle JNI_GetCreatedJavaVM_MH = Linker.nativeLinker()
                .downcallHandle(
                        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.ADDRESS)
                )
                .bindTo(jniGetCreatedJavaVM_FP);
        Arena global = Arena.global();
        MemorySegment vm = global.allocate(ValueLayout.ADDRESS);
        MemorySegment numVMs = global.allocate(ValueLayout.JAVA_INT, 0);
        int i = (int) JNI_GetCreatedJavaVM_MH.invokeExact(vm, 1, numVMs);
        return vm.get(ValueLayout.ADDRESS, 0);
    }

    private static MethodHandle initGetJNIEnvMH() {
        MemorySegment JNU_GetEnv_FP = SymbolLookup.loaderLookup()
                .find("JNU_GetEnv")
                .get();
        return Linker.nativeLinker()
                .downcallHandle(FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT))
                .bindTo(JNU_GetEnv_FP);
    }
}
