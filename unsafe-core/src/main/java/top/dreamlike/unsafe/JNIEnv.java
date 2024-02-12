package top.dreamlike.unsafe;

import top.dreamlike.unsafe.helper.JValue;
import top.dreamlike.unsafe.helper.NativeHelper;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import static java.lang.StringTemplate.STR;
import static top.dreamlike.unsafe.helper.NativeHelper.throwable;

public class JNIEnv {

    private final static MemorySegment MAIN_VM_Pointer = throwable(JNIEnv::initMainVM);

    private final static long JNI_VERSION = 0x00150000;

    private final static MethodHandle GET_JNIENV_MH = throwable(JNIEnv::initGetJNIEnvMH);

    private final static MethodHandle NewGlobalRef_MH = Linker.nativeLinker().downcallHandle(FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));

    private final static MethodHandle DeleteGlobalRef_MH = Linker.nativeLinker().downcallHandle(FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS));

    /**
     * 只用于调用系统加载器加载的类中的静态方法
     * 原因在于：对应的jni实现里面先获取类加载器是查找vframe顶层的栈帧，拿到这个栈帧的owner，然后用这个owner所属的类加载器去找到对应的类
     * 而在这里顶层栈帧归属于MethodHandle,所以找到的类加载器是系统类加载器，所以只能调用系统类加载器加载的类
     */
    public final static MemorySegment CallStaticMethodByNameFp = SymbolLookup.loaderLookup().find("JNU_CallStaticMethodByName").get();

    public final static MemorySegment CallMethodByNameFp = SymbolLookup.loaderLookup().find("JNU_CallMethodByName").get();


    private final static MethodHandle CallStaticMethodByNameEmptyArgsMethodHandle = Linker.nativeLinker()
            .downcallHandle(FunctionDescriptor.of(
                    JValue.Nativejvalue,
                    /*JNIEnv *env */ValueLayout.ADDRESS,
                    /*jboolean *hasException*/ValueLayout.ADDRESS,
                    /* const char *classname*/ ValueLayout.ADDRESS,
                    /*const char *name*/ ValueLayout.ADDRESS,
                    /* const char *signature*/ ValueLayout.ADDRESS
            )).bindTo(CallStaticMethodByNameFp);

    private final static MethodHandle CallMethodByNameEmptyArgsMethodHandle = Linker.nativeLinker()
            .downcallHandle(FunctionDescriptor.of(
                    JValue.Nativejvalue,
                    /*JNIEnv *env */ValueLayout.ADDRESS,
                    /*jboolean *hasException*/ValueLayout.ADDRESS,
                    /* jobject*/ ValueLayout.ADDRESS,
                    /*const char *name*/ ValueLayout.ADDRESS,
                    /* const char *signature*/ ValueLayout.ADDRESS
            )).bindTo(CallMethodByNameFp);

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

    /**
     *
     * 只用于调用系统加载器加载的类中的静态方法
     * 原因在于：对应的jni实现里面先获取类加载器是查找vframe顶层的栈帧，拿到这个栈帧的owner，然后用这个owner所属的类加载器去找到对应的类
     * 而在这里顶层栈帧归属于MethodHandle,所以找到的类加载器是系统类加载器，所以只能调用系统类加载器加载的类
     *
     * @param method 需要调用的方法
     */
    public JValue CallStaticMethodByName(Method method) {
        Class<?> ownerClass = method.getDeclaringClass();
        if (ownerClass.getClassLoader() != null) {
            throw new IllegalArgumentException("only support system class loader");
        }
        if (method.getParameters().length != 0) {
            throw new IllegalArgumentException("only support empty args method");
        }
        if (Modifier.isStatic(method.getModifiers())) {
            throw new IllegalArgumentException("only support static method");
        }
        String methodName = method.getName();
        String className = ownerClass.getName().replace(".", "/");
        String returnSig = NativeHelper.classToSig(method.getReturnType());
        MemorySegment memorySegment = throwable(() ->
                (MemorySegment) CallStaticMethodByNameEmptyArgsMethodHandle.invokeExact(
                        jniEnvPointer,
                        MemorySegment.NULL,
                        arena.allocateUtf8String(className),
                        arena.allocateUtf8String(methodName),
                        arena.allocateUtf8String(STR."()\{returnSig}")
                )
        );
        return new JValue(memorySegment);
    }

    public JValue CallStaticMethodByName(Method method, MemorySegment jobject) {
        if (method.getParameters().length != 0) {
            throw new IllegalArgumentException("only support empty args method");
        }
        if (Modifier.isStatic(method.getModifiers())) {
            throw new IllegalArgumentException("only support static method");
        }
        Class<?> ownerClass = method.getDeclaringClass();
        String methodName = method.getName();
        String className = ownerClass.getName().replace(".", "/");
        String returnSig = NativeHelper.classToSig(method.getReturnType());
        MemorySegment memorySegment = throwable(() ->
                (MemorySegment) CallMethodByNameEmptyArgsMethodHandle.invokeExact(
                        jniEnvPointer,
                        MemorySegment.NULL,
                        jobject,
                        arena.allocateUtf8String(methodName),
                        arena.allocateUtf8String(STR."()\{returnSig}")
                )
        );
        return new JValue(memorySegment);
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
