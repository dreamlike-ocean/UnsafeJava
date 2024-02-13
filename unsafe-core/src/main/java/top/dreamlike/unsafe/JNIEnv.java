package top.dreamlike.unsafe;

import top.dreamlike.unsafe.helper.GlobalRef;
import top.dreamlike.unsafe.helper.JValue;
import top.dreamlike.unsafe.helper.NativeHelper;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import static java.lang.StringTemplate.STR;
import static top.dreamlike.unsafe.JNIEnvFunctions.*;
import static top.dreamlike.unsafe.helper.NativeHelper.throwable;

public class JNIEnv {

    private final static MemorySegment MAIN_VM_Pointer = throwable(JNIEnv::initMainVM);

    public final static int JNI_VERSION = 0x00150000;

    private final static MethodHandle GET_JNIENV_MH = throwable(JNIEnv::initGetJNIEnvMH);

    private final static MethodHandle GetStringPlatformCharsMH = throwable(() -> {
        MemorySegment JNU_GetStringPlatformCharsFP = SymbolLookup.loaderLookup()
                .find("JNU_GetStringPlatformChars")
                .get();
        return Linker.nativeLinker()
                .downcallHandle(FunctionDescriptor.of(
                        /*const char * */ValueLayout.ADDRESS,
                        /*JNIEnv *env */ValueLayout.ADDRESS,
                        /*jstring str*/ ValueLayout.ADDRESS,
                        /*jboolean *isCopy*/ ValueLayout.ADDRESS
                )).bindTo(JNU_GetStringPlatformCharsFP);
    });

    private final static MethodHandle GetStaticFieldByName = throwable(() -> {
        MemorySegment JNU_GetStaticFieldByNameFP = SymbolLookup.loaderLookup()
                .find("JNU_GetStaticFieldByName")
                .get();
        return Linker.nativeLinker()
                .downcallHandle(FunctionDescriptor.of(
                        /*jobject*/ValueLayout.JAVA_LONG,
                        /*JNIEnv *env */ValueLayout.ADDRESS,
                        /*hasException*/ ValueLayout.ADDRESS,
                        /*const char *classname*/ ValueLayout.ADDRESS,
                        /*const char *name*/ ValueLayout.ADDRESS,
                        /* const char *signature*/ ValueLayout.ADDRESS
                )).bindTo(JNU_GetStaticFieldByNameFP);
    });


    /**
     * 只用于调用系统加载器加载的类中的静态方法
     * 原因在于：对应的jni实现里面先获取类加载器是查找vframe顶层的栈帧，拿到这个栈帧的owner，然后用这个owner所属的类加载器去找到对应的类
     * 而在这里顶层栈帧归属于MethodHandle,所以找到的类加载器是系统类加载器，所以只能调用系统类加载器加载的类
     */
    public final static MemorySegment CallStaticMethodByNameFp = SymbolLookup.loaderLookup().find("JNU_CallStaticMethodByName").get();

    public final static MemorySegment CallMethodByNameFp = SymbolLookup.loaderLookup().find("JNU_CallMethodByName").get();


    private final static MethodHandle CallStaticMethodByNameEmptyArgsMethodHandle = Linker.nativeLinker()
            .downcallHandle(FunctionDescriptor.of(
                    ValueLayout.JAVA_LONG,
                    /*JNIEnv *env */ValueLayout.ADDRESS,
                    /*jboolean *hasException*/ValueLayout.ADDRESS,
                    /* const char *classname*/ ValueLayout.ADDRESS,
                    /*const char *name*/ ValueLayout.ADDRESS,
                    /* const char *signature*/ ValueLayout.ADDRESS
            )).bindTo(CallStaticMethodByNameFp);

    private final static MethodHandle CallMethodByNameEmptyArgsMethodHandle = Linker.nativeLinker()
            .downcallHandle(FunctionDescriptor.of(
                    ValueLayout.JAVA_LONG,
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

    private final static MethodHandle ToStringMh = throwable(() -> {
        MemorySegment JNU_ToStringFP = SymbolLookup.loaderLookup()
                .find("JNU_ToString")
                .get();
        return Linker.nativeLinker()
                .downcallHandle(FunctionDescriptor.of(
                        /*jstring*/ValueLayout.ADDRESS,
                        /*JNIEnv *env */ValueLayout.ADDRESS,
                        /*jobject obj*/ ValueLayout.ADDRESS
                )).bindTo(JNU_ToStringFP);
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

    public MemorySegment FindClass(Class c) {
        boolean isSystemClassloader = c.getClassLoader() == null;
        if (isSystemClassloader) {
            return throwable(() -> (MemorySegment) FindClassMH.invokeExact(
                    functions.FindClassFp,
                    jniEnvPointer,
                    arena.allocateUtf8String(c.getName().replace(".", "/")))
            );
        }

        return throwable(() -> {
            JValue jobject = CallStaticMethodByName(Thread.class.getMethod("currentThread"));
            JValue classLoaderJobject = CallMethodByName(Thread.class.getMethod("getContextClassLoader"), jobject.toPtr());

            try (GlobalRef classLoaderJobjectRef = new GlobalRef(this, classLoaderJobject.toPtr());
                 GlobalRef classNameRef = new GlobalRef(this, cstrToJstring((arena.allocateUtf8String(c.getName()))))
            ) {
//                Class<?> name = Class.forName("top.dreamlike.unsafe.JNIEnv", true, loader);
                return (MemorySegment) JNIEnvExt.ClassLoaderForNameMH.invokeExact(
                        jniEnvPointer,
                        MemorySegment.NULL,
                        arena.allocateUtf8String(Class.class.getName().replace(".", "/")),
                        arena.allocateUtf8String("forName"),
                        arena.allocateUtf8String("(Ljava/lang/String;ZLjava/lang/ClassLoader;)Ljava/lang/Class;"),
                        classNameRef.ref(),
                        true,
                        classLoaderJobjectRef.ref()
                );
            }
        });
    }


    public JValue GetStaticFieldByName(Field field) {
        if (!Modifier.isStatic(field.getModifiers())) {
            throw new IllegalArgumentException("only support static field");
        }

        Class<?> aClass = field.getDeclaringClass();
        String className = aClass.getName().replace(".", "/");
        String signature = NativeHelper.classToSig(field.getType());
        boolean isSystemLoader = aClass.getClassLoader() == null;
        if (isSystemLoader) {
            long value = throwable(() -> (long) GetStaticFieldByName.invokeExact(
                    jniEnvPointer,
                    MemorySegment.NULL,
                    arena.allocateUtf8String(className),
                    arena.allocateUtf8String(field.getName()),
                    arena.allocateUtf8String(signature)
            ));
            return new JValue(value);
        }

        return GetStaticFieldByNameForOtherClassloader(field);
    }

    private JValue GetStaticFieldByNameForOtherClassloader(Field field) {
        return throwable(() -> {
            var clsRef = FindClass(field.getDeclaringClass());
            var fidRef = (MemorySegment) GetStaticFieldID_MH.invokeExact(
                    functions.GetStaticFieldIDFp,
                    jniEnvPointer,
                    clsRef,
                    arena.allocateUtf8String(field.getName()),
                    arena.allocateUtf8String(NativeHelper.classToSig(field.getType()))
            );
                long value = switch (field.getType().getName()) {
                    case "boolean" -> (long)GetStaticBooleanField_MH.invokeExact(functions.GetStaticBooleanFieldFp, jniEnvPointer, clsRef, fidRef);
                    case "byte" -> (long)GetStaticByteField_MH.invokeExact(functions.GetStaticByteFieldFp, jniEnvPointer, clsRef, fidRef);
                    case "char" -> (long)GetStaticCharField_MH.invokeExact(functions.GetStaticCharFieldFp, jniEnvPointer, clsRef, fidRef);
                    case "short" -> (long)GetStaticShortField_MH.invokeExact(functions.GetStaticShortFieldFp, jniEnvPointer, clsRef, fidRef);
                    case "int" -> (long)GetStaticIntField_MH.invokeExact(functions.GetStaticIntFieldFp, jniEnvPointer, clsRef, fidRef);
                    case "long" -> (long)GetStaticLongField_MH.invokeExact(functions.GetStaticLongFieldFp, jniEnvPointer, clsRef, fidRef);
                    case "float" -> (long)GetStaticFloatField_MH.invokeExact(functions.GetStaticFloatFieldFp, jniEnvPointer, clsRef, fidRef);
                    case "double" -> (long)GetStaticDoubleField_MH.invokeExact(functions.GetStaticDoubleFieldFp, jniEnvPointer, clsRef, fidRef);
                    default -> (long) GetStaticObjectField_MH.invokeExact(functions.GetStaticObjectFieldFp, jniEnvPointer, clsRef, fidRef);
                };
                return new JValue(value);

        });
    }

    public void SetStaticFieldByName(Field field, JValue value) {
        if (!Modifier.isStatic(field.getModifiers())) {
            throw new IllegalArgumentException("only support static field");
        }
        throwable(() -> {
           Class<?> aClass = field.getDeclaringClass();
           MemorySegment clsRef = FindClass(aClass);
           var fidRef = (MemorySegment) GetStaticFieldID_MH.invokeExact(
                   functions.GetStaticFieldIDFp,
                   jniEnvPointer,
                   clsRef,
                   arena.allocateUtf8String(field.getName()),
                   arena.allocateUtf8String(NativeHelper.classToSig(field.getType()))
           );
            switch (field.getType().getName()) {
                case "boolean" -> SetStaticBooleanField_MH.invokeExact(functions.SetStaticBooleanFieldFp, jniEnvPointer, clsRef, fidRef, value.getBoolean());
                case "byte" -> SetStaticByteField_MH.invokeExact(functions.SetStaticByteFieldFp, jniEnvPointer, clsRef, fidRef, value.getByte());
                case "char" -> SetStaticCharField_MH.invokeExact(functions.SetStaticCharFieldFp, jniEnvPointer, clsRef, fidRef, value.getChar());
                case "short" -> SetStaticShortField_MH.invokeExact(functions.SetStaticShortFieldFp, jniEnvPointer, clsRef, fidRef, value.getShort());
                case "int" -> SetStaticIntField_MH.invokeExact(functions.SetStaticIntFieldFp, jniEnvPointer, clsRef, fidRef, value.getInt());
                case "long" -> SetStaticLongField_MH.invokeExact(functions.SetStaticLongFieldFp, jniEnvPointer, clsRef, fidRef, value.getLong());
                case "float" -> SetStaticFloatField_MH.invokeExact(functions.SetStaticFloatFieldFp, jniEnvPointer, clsRef, fidRef, value.getFloat());
                case "double" -> SetStaticDoubleField_MH.invokeExact(functions.SetStaticDoubleFieldFp, jniEnvPointer, clsRef, fidRef, value.getDouble());
                default -> SetStaticObjectField_MH.invokeExact(functions.SetStaticObjectFieldFp, jniEnvPointer, clsRef, fidRef, value.toPtr());
            }
       });

    }

    public MemorySegment ToString(MemorySegment jobject) {
        return throwable(() -> (MemorySegment) ToStringMh.invokeExact(jniEnvPointer, jobject));
    }

    public MemorySegment cstrToJstring(MemorySegment cstr) {
        return throwable(() -> (MemorySegment) NewStringPlatform.invokeExact(jniEnvPointer, cstr));
    }

    public String jstringToCstr(MemorySegment jstring) {
        MemorySegment memorySegment = throwable(() -> (MemorySegment) GetStringPlatformCharsMH.invokeExact(jniEnvPointer, jstring, MemorySegment.NULL));
        return memorySegment.reinterpret(Long.MAX_VALUE).getUtf8String(0);
    }

    /**
     * 只用于调用系统加载器加载的类中的静态方法
     * 原因在于：对应的jni实现里面先获取类加载器是查找vframe顶层的栈帧，拿到这个栈帧的owner，然后用这个owner所属的类加载器去找到对应的类
     * 而在这里顶层栈帧归属于MethodHandle,所以找到的类加载器是系统类加载器，所以只能调用系统类加载器加载的类
     * todo 适配自定义类
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
        if (!Modifier.isStatic(method.getModifiers())) {
            throw new IllegalArgumentException("only support static method");
        }
        String methodName = method.getName();
        String className = ownerClass.getName().replace(".", "/");
        String returnSig = NativeHelper.classToSig(method.getReturnType());
        long jvalue = throwable(() ->
                (long) CallStaticMethodByNameEmptyArgsMethodHandle.invokeExact(
                        jniEnvPointer,
                        MemorySegment.NULL,
                        arena.allocateUtf8String(className),
                        arena.allocateUtf8String(methodName),
                        arena.allocateUtf8String(STR."()\{returnSig}")
                )
        );
        return new JValue(jvalue);
    }

    public JValue CallMethodByName(Method method, MemorySegment jobject) {
        if (method.getParameters().length != 0) {
            throw new IllegalArgumentException("only support empty args method");
        }
        if (Modifier.isStatic(method.getModifiers())) {
            throw new IllegalArgumentException("only support static method");
        }
        String methodName = method.getName();
        String returnSig = NativeHelper.classToSig(method.getReturnType());
        long memorySegment = throwable(() ->
                (long) CallMethodByNameEmptyArgsMethodHandle.invokeExact(
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
        MemorySegment numVMs = global.allocate(ValueLayout.JAVA_INT);
        //jdk22和其他版本 兼容使用
        numVMs.set(ValueLayout.JAVA_INT, 0, 0);
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
