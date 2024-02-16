package top.dreamlike.unsafe.jni;

import top.dreamlike.unsafe.helper.GlobalRef;
import top.dreamlike.unsafe.helper.JValue;
import top.dreamlike.unsafe.helper.NativeHelper;

import java.io.File;
import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.reflect.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.logging.Filter;
import java.util.stream.Collectors;

import static java.lang.StringTemplate.STR;
import static top.dreamlike.unsafe.jni.JNIEnvFunctions.*;
import static top.dreamlike.unsafe.helper.NativeHelper.throwable;

public class JNIEnv {

    private final static MemorySegment MAIN_VM_Pointer = throwable(JNIEnv::initMainVM);

    public final static int JNI_VERSION = 0x00150000;

    private final static MethodHandle GET_JNIENV_MH = throwable(JNIEnv::initGetJNIEnvMH);

    private final static ThreadLocal<Object> jniToJava =  new ThreadLocal<>();

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


    private final SegmentAllocator allocator;
    private final MemorySegment jniEnvPointer;

    public final JNIEnvFunctions functions;


    public JNIEnv(SegmentAllocator allocator) {
        this.allocator = allocator;
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

    public GlobalRef FindClass(Class c) {
        boolean isSystemClassloader = c.getClassLoader() == null;
        if (isSystemClassloader) {
            return throwable(() -> new GlobalRef(this,  (MemorySegment) FindClassMH.invokeExact(
                    functions.FindClassFp,
                    jniEnvPointer,
                    allocator.allocateUtf8String(c.getName().replace(".", "/"))))
            );
        }

        return throwable(() ->
        {
            try (GlobalRef threadRef = CallStaticMethodByName(Thread.class.getMethod("currentThread"));
                 GlobalRef classLoaderJobjectRef = CallMethodByName(Thread.class.getMethod("getContextClassLoader"), threadRef.ref());
                 GlobalRef classNameRef = cstrToJstring((allocator.allocateUtf8String(c.getName())))
            ) {
//                Class<?> name = Class.forName("top.dreamlike.unsafe.jni.JNIEnv", true, loader);
                MemorySegment segment = allocator.allocate(JValue.jvalueLayout);
                segment.set(ValueLayout.JAVA_BOOLEAN, 0, false);
                return CallStaticMethodByName(Class.class.getDeclaredMethod("forName", String.class, boolean.class, ClassLoader.class),
                        new JValue(classNameRef.ref().address()),
                        new JValue(segment.get(ValueLayout.JAVA_LONG, 0)),
                        new JValue(classLoaderJobjectRef.ref().address())
                );
            }
        });
    }


    public GlobalRef GetStaticFieldByName(Field field) {
        if (!Modifier.isStatic(field.getModifiers())) {
            throw new IllegalArgumentException("only support static field");
        }
        return throwable(() -> {

            try(var clsRef = FindClass(field.getDeclaringClass())){
                var fidRef = (MemorySegment) GetStaticFieldID_MH.invokeExact(
                        functions.GetStaticFieldIDFp,
                        jniEnvPointer,
                        clsRef.ref(),
                        allocator.allocateUtf8String(field.getName()),
                        allocator.allocateUtf8String(NativeHelper.classToSig(field.getType()))
                );
                boolean isRef = false;
                long value = switch (field.getType().getName()) {
                    case "boolean" ->
                            (long) GetStaticBooleanField_MH.invokeExact(functions.GetStaticBooleanFieldFp, jniEnvPointer, clsRef.ref(), fidRef);
                    case "byte" ->
                            (long) GetStaticByteField_MH.invokeExact(functions.GetStaticByteFieldFp, jniEnvPointer, clsRef.ref(), fidRef);
                    case "char" ->
                            (long) GetStaticCharField_MH.invokeExact(functions.GetStaticCharFieldFp, jniEnvPointer, clsRef.ref(), fidRef);
                    case "short" ->
                            (long) GetStaticShortField_MH.invokeExact(functions.GetStaticShortFieldFp, jniEnvPointer, clsRef.ref(), fidRef);
                    case "int" ->
                            (long) GetStaticIntField_MH.invokeExact(functions.GetStaticIntFieldFp, jniEnvPointer, clsRef.ref(), fidRef);
                    case "long" ->
                            (long) GetStaticLongField_MH.invokeExact(functions.GetStaticLongFieldFp, jniEnvPointer, clsRef.ref(), fidRef);
                    case "float" ->
                            (long) GetStaticFloatField_MH.invokeExact(functions.GetStaticFloatFieldFp, jniEnvPointer, clsRef.ref(), fidRef);
                    case "double" ->
                            (long) GetStaticDoubleField_MH.invokeExact(functions.GetStaticDoubleFieldFp, jniEnvPointer, clsRef.ref(), fidRef);
                    default ->{
                        isRef = true;
                        yield (long) GetStaticObjectField_MH.invokeExact(functions.GetStaticObjectFieldFp, jniEnvPointer, clsRef.ref(), fidRef);
                    }

                };
                return isRef ? new GlobalRef(this, MemorySegment.ofAddress(value)) : new GlobalRef(this, new JValue(value));
            }
        });
    }

    public void SetStaticFieldByName(Field field, GlobalRef value) {
        if (!Modifier.isStatic(field.getModifiers())) {
            throw new IllegalArgumentException("only support static field");
        }
        throwable(() -> {
            Class<?> aClass = field.getDeclaringClass();

            try(GlobalRef clsRef = FindClass(aClass);) {
                var fidRef = (MemorySegment) GetStaticFieldID_MH.invokeExact(
                        functions.GetStaticFieldIDFp,
                        jniEnvPointer,
                        clsRef.ref(),
                        allocator.allocateUtf8String(field.getName()),
                        allocator.allocateUtf8String(NativeHelper.classToSig(field.getType()))
                );
                switch (field.getType().getName()) {
                    case "boolean" ->
                            SetStaticBooleanField_MH.invokeExact(functions.SetStaticBooleanFieldFp, jniEnvPointer, clsRef.ref(), fidRef, value.jValue.getBoolean());
                    case "byte" ->
                            SetStaticByteField_MH.invokeExact(functions.SetStaticByteFieldFp, jniEnvPointer, clsRef.ref(), fidRef, value.jValue.getByte());
                    case "char" ->
                            SetStaticCharField_MH.invokeExact(functions.SetStaticCharFieldFp, jniEnvPointer, clsRef.ref(), fidRef, value.jValue.getChar());
                    case "short" ->
                            SetStaticShortField_MH.invokeExact(functions.SetStaticShortFieldFp, jniEnvPointer, clsRef.ref(), fidRef, value.jValue.getShort());
                    case "int" ->
                            SetStaticIntField_MH.invokeExact(functions.SetStaticIntFieldFp, jniEnvPointer, clsRef.ref(), fidRef, value.jValue.getInt());
                    case "long" ->
                            SetStaticLongField_MH.invokeExact(functions.SetStaticLongFieldFp, jniEnvPointer, clsRef.ref(), fidRef, value.jValue.getLong());
                    case "float" ->
                            SetStaticFloatField_MH.invokeExact(functions.SetStaticFloatFieldFp, jniEnvPointer, clsRef.ref(), fidRef, value.jValue.getFloat());
                    case "double" ->
                            SetStaticDoubleField_MH.invokeExact(functions.SetStaticDoubleFieldFp, jniEnvPointer, clsRef.ref(), fidRef, value.jValue.getDouble());
                    default ->
                            SetStaticObjectField_MH.invokeExact(functions.SetStaticObjectFieldFp, jniEnvPointer, clsRef.ref(), fidRef, value.ref());
                }
            }

        });
    }

    public GlobalRef GetFieldByName(Field field, GlobalRef jobject) {
        if (Modifier.isStatic(field.getModifiers())) {
            throw new IllegalArgumentException("only support not static field");
        }
        return throwable(() -> {
            try(var clsRef = FindClass(field.getDeclaringClass())) {
                var fidRef = (MemorySegment) GetFieldId.invokeExact(
                        functions.GetFieldIDFp,
                        jniEnvPointer,
                        clsRef.ref(),
                        allocator.allocateUtf8String(field.getName()),
                        allocator.allocateUtf8String(NativeHelper.classToSig(field.getType()))
                );
                boolean isRef = false;
                long value = switch (field.getType().getName()) {
                    case "boolean" ->
                            (long) GetBooleanField.invokeExact(functions.GetBooleanFieldFp, jniEnvPointer, jobject.ref(), fidRef);
                    case "byte" ->
                            (long) GetByteField.invokeExact(functions.GetByteFieldFp, jniEnvPointer, jobject.ref(), fidRef);
                    case "char" ->
                            (long) GetCharField.invokeExact(functions.GetCharFieldFp, jniEnvPointer, jobject.ref(), fidRef);
                    case "short" ->
                            (long) GetShortField.invokeExact(functions.GetShortFieldFp, jniEnvPointer, jobject.ref(), fidRef);
                    case "int" ->
                            (long) GetIntField.invokeExact(functions.GetIntFieldFp, jniEnvPointer, jobject.ref(), fidRef);
                    case "long" ->
                            (long) GetLongField.invokeExact(functions.GetLongFieldFp, jniEnvPointer, jobject.ref(), fidRef);
                    case "float" ->
                            (long) GetFloatField.invokeExact(functions.GetFloatFieldFp, jniEnvPointer, jobject.ref(), fidRef);
                    case "double" ->
                            (long) GetDoubleField.invokeExact(functions.GetDoubleFieldFp, jniEnvPointer, jobject.ref(), fidRef);
                    default ->{
                        isRef = true;
                        yield (long) GetObjectField.invokeExact(functions.GetObjectFieldFp, jniEnvPointer, jobject.ref(), fidRef);
                    }

                };
                return isRef ? new GlobalRef(this, MemorySegment.ofAddress(value)) : new GlobalRef(this, new JValue(value));
            }
        });
    }

    public void SetFieldByName(Field field, GlobalRef target, GlobalRef fieldValue) {
        if (Modifier.isStatic(field.getModifiers())) {
            throw new IllegalArgumentException("only support not static field");
        }
        throwable(() -> {
            try (var clsRef = FindClass(field.getDeclaringClass())) {
                var fidRef = (MemorySegment) GetFieldId.invokeExact(
                        functions.GetFieldIDFp,
                        jniEnvPointer,
                        clsRef.ref(),
                        allocator.allocateUtf8String(field.getName()),
                        allocator.allocateUtf8String(NativeHelper.classToSig(field.getType()))
                );
                switch (field.getType().getName()) {
                    case "boolean" ->
                            SetBooleanField.invokeExact(functions.SetBooleanFieldFp, jniEnvPointer, target.ref(), fidRef, fieldValue.jValue.getBoolean());
                    case "byte" ->
                            SetByteField.invokeExact(functions.SetByteFieldFp, jniEnvPointer, target.ref(), fidRef, fieldValue.jValue.getByte());
                    case "char" ->
                            SetCharField.invokeExact(functions.SetCharFieldFp, jniEnvPointer, target.ref(), fidRef, fieldValue.jValue.getChar());
                    case "short" ->
                            SetShortField.invokeExact(functions.SetShortFieldFp, jniEnvPointer, target.ref(), fidRef, fieldValue.jValue.getShort());
                    case "int" ->
                            SetIntField.invokeExact(functions.SetIntFieldFp, jniEnvPointer, target.ref(), fidRef, fieldValue.jValue.getInt());
                    case "long" ->
                            SetLongField.invokeExact(functions.SetLongFieldFp, jniEnvPointer, target.ref(), fidRef, fieldValue.jValue.getLong());
                    case "float" ->
                            SetFloatField.invokeExact(functions.SetFloatFieldFp, jniEnvPointer, target.ref(), fidRef, fieldValue.jValue.getFloat());
                    case "double" ->
                            SetDoubleField.invokeExact(functions.SetDoubleFieldFp, jniEnvPointer, target.ref(), fidRef, fieldValue.jValue.getDouble());
                    default ->
                            SetObjectField.invokeExact(functions.SetObjectFieldFp, jniEnvPointer, target.ref(), fidRef, fieldValue.ref());
                }
            }
        });
    }


    private GlobalRef ToString(MemorySegment jobject) {
        return throwable(() -> new GlobalRef(this, (MemorySegment) ToStringMh.invokeExact(jniEnvPointer, jobject)));
    }

    private GlobalRef cstrToJstring(MemorySegment cstr) {
        return throwable(() -> new GlobalRef(this,  (MemorySegment) NewStringPlatform.invokeExact(jniEnvPointer, cstr)));
    }

    private String jstringToCstr(MemorySegment jstring) {

        MemorySegment memorySegment = throwable(() -> {

            return (MemorySegment) GetStringPlatformCharsMH.invokeExact(jniEnvPointer, jstring, MemorySegment.NULL);

        });
        return memorySegment.reinterpret(Long.MAX_VALUE).getUtf8String(0);
    }

    public GlobalRef CallStaticMethodByName(Method method) {
        return CallStaticMethodByName(method, MemorySegment.NULL, STR."()\{NativeHelper.classToSig(method.getReturnType())}");
    }

    public GlobalRef CallStaticMethodByName(Method method, MemorySegment jvalues) {
        String paramSig = Arrays.stream(method.getParameters())
                .map(Parameter::getType)
                .map(NativeHelper::classToSig)
                .collect(Collectors.joining());
        return CallStaticMethodByName(method, jvalues, STR."(\{paramSig})\{NativeHelper.classToSig(method.getReturnType())}");
    }

    public GlobalRef CallStaticMethodByName(Method method, JValue... jvalues) {
        String paramSig = Arrays.stream(method.getParameters())
                .map(Parameter::getType)
                .map(NativeHelper::classToSig)
                .collect(Collectors.joining());
        long[] longs = new long[jvalues.length];
        for (int i = 0; i < jvalues.length; i++) {
            longs[i] = jvalues[i].getLong();
        }
        MemorySegment jValuesPtr = allocator.allocateArray(JValue.jvalueLayout, jvalues.length);
        jValuesPtr.copyFrom(MemorySegment.ofArray(longs));
        return CallStaticMethodByName(method, jValuesPtr, STR."(\{paramSig})\{NativeHelper.classToSig(method.getReturnType())}");
    }

    /**
     * 只用于调用系统加载器加载的类中的静态方法
     * 原因在于：对应的jni实现里面先获取类加载器是查找vframe顶层的栈帧，拿到这个栈帧的owner，然后用这个owner所属的类加载器去找到对应的类
     * 而在这里顶层栈帧归属于MethodHandle,所以找到的类加载器是系统类加载器，所以只能调用系统类加载器加载的类
     *
     * @param method 需要调用的方法
     */
    public GlobalRef CallStaticMethodByName(Method method, MemorySegment jvalues, String sig) {
        if (method.getParameters().length * JValue.jvalueLayout.byteSize() != jvalues.byteSize()) {
            throw new IllegalArgumentException("jvalues size not match");
        }
        if (!Modifier.isStatic(method.getModifiers())) {
            throw new IllegalArgumentException("only support static method");
        }
        Class<?> ownerClass = method.getDeclaringClass();
        String methodName = method.getName();
        return throwable(() -> {
            //todo解析错误的问题
            try (GlobalRef jclassRef = FindClass(ownerClass)) {
                MemorySegment mid = (MemorySegment) GetStaticMethodID_MH.invokeExact(functions.GetStaticMethodIDFp, jniEnvPointer, jclassRef.ref(), allocator.allocateUtf8String(methodName), allocator.allocateUtf8String(sig));
                boolean isRef = false;
                long jvalue = switch (method.getReturnType().getName()) {
                    case "void" -> {
                        CallStaticVoidMethodA_MH.invokeExact(functions.CallStaticVoidMethodAFp, jniEnvPointer, jclassRef.ref(), mid, jvalues);
                        yield 0L;
                    }
                    case "int" ->
                            (long) CallStaticIntMethodA_MH.invokeExact(functions.CallStaticIntMethodAFp, jniEnvPointer, jclassRef.ref(), mid, jvalues);
                    case "boolean" ->
                            (long) CallStaticBooleanMethodA_MH.invokeExact(functions.CallStaticBooleanMethodAFp, jniEnvPointer, jclassRef.ref(), mid, jvalues);
                    case "byte" ->
                            (long) CallStaticByteMethodA_MH.invokeExact(functions.CallStaticByteMethodAFp, jniEnvPointer, jclassRef.ref(), mid, jvalues);
                    case "char" ->
                            (long) CallStaticCharMethodA_MH.invokeExact(functions.CallStaticCharMethodAFp, jniEnvPointer, jclassRef.ref(), mid, jvalues);
                    case "short" ->
                            (long) CallStaticShortMethodA_MH.invokeExact(functions.CallStaticShortMethodAFp, jniEnvPointer, jclassRef.ref(), mid, jvalues);
                    case "long" ->
                            (long) CallStaticLongMethodA_MH.invokeExact(functions.CallStaticLongMethodAFp, jniEnvPointer, jclassRef.ref(), mid, jvalues);
                    case "float" ->
                            (long) CallStaticFloatMethodA_MH.invokeExact(functions.CallStaticFloatMethodAFp, jniEnvPointer, jclassRef.ref(), mid, jvalues);
                    case "double" ->
                            (long) CallStaticDoubleMethodA_MH.invokeExact(functions.CallStaticDoubleMethodAFp, jniEnvPointer, jclassRef.ref(), mid, jvalues);
                    default -> {
                        isRef = true;
                        yield (long) CallStaticObjectMethodA_MH.invokeExact(functions.CallStaticObjectMethodAFp, jniEnvPointer, jclassRef.ref(), mid, jvalues);
                    }
                };
                return isRef ? new GlobalRef(this, MemorySegment.ofAddress(jvalue)) : new GlobalRef(this, new JValue(jvalue));
            }
        });
    }

    public GlobalRef CallMethodByName(Method method, MemorySegment jobject) {
        return CallMethodByName(method, jobject, MemorySegment.NULL, STR."()\{NativeHelper.classToSig(method.getReturnType())}");
    }

    public GlobalRef CallMethodByName(Method method, MemorySegment jobject, MemorySegment jvalues) {
        String paramSig = Arrays.stream(method.getParameters())
                .map(Parameter::getType)
                .map(NativeHelper::classToSig)
                .collect(Collectors.joining());
        return CallMethodByName(method, jobject, jvalues, STR."(\{paramSig})\{NativeHelper.classToSig(method.getReturnType())}");
    }

    public GlobalRef CallMethodByName(Method method, MemorySegment jobject, MemorySegment jvalues, String sig) {
        if (method.getParameters().length * JValue.jvalueLayout.byteSize() != jvalues.byteSize()) {
            throw new IllegalArgumentException("jvalues size not match");
        }
        if (Modifier.isStatic(method.getModifiers())) {
            throw new IllegalArgumentException("only support not static method");
        }
        String methodName = method.getName();
        return throwable(() -> {
            boolean isRef = false;
            MemorySegment clazz = (MemorySegment) GetObjectClass_MH.invokeExact(functions.GetObjectClassFp, jniEnvPointer, jobject);
            try (GlobalRef ref = new GlobalRef(this, clazz);) {
                MemorySegment mid = (MemorySegment) GetMethodID_MH.invokeExact(functions.GetMethodIDFp, jniEnvPointer, ref.ref(), allocator.allocateUtf8String(methodName), allocator.allocateUtf8String(sig));
                long returnValue = switch (method.getReturnType().getName()) {
                    case "void" -> {
                        CallVoidMethodA_MH.invokeExact(functions.CallVoidMethodAFp, jniEnvPointer, jobject, mid, jvalues);
                        yield 0L;
                    }
                    case "int" ->
                            (long) CallIntMethodA_MH.invokeExact(functions.CallIntMethodAFp, jniEnvPointer, jobject, mid, jvalues);
                    case "boolean" ->
                            (long) CallBooleanMethodA_MH.invokeExact(functions.CallBooleanMethodAFp, jniEnvPointer, mid, jvalues);
                    case "byte" ->
                            (long) CallByteMethodA_MH.invokeExact(functions.CallByteMethodAFp, jniEnvPointer, jobject, mid, jvalues);
                    case "char" ->
                            (long) CallCharMethodA_MH.invokeExact(functions.CallCharMethodAFp, jniEnvPointer, jobject, mid, jvalues);
                    case "short" ->
                            (long) CallShortMethodA_MH.invokeExact(functions.CallShortMethodAFp, jniEnvPointer, jobject, mid, jvalues);
                    case "long" ->
                            (long) CallLongMethodA_MH.invokeExact(functions.CallLongMethodAFp, jniEnvPointer, jobject, mid, jvalues);
                    case "float" ->
                            (long) CallFloatMethodA_MH.invokeExact(functions.CallFloatMethodAFp, jniEnvPointer, jobject, mid, jvalues);
                    case "double" ->
                            (long) CallDoubleMethodA_MH.invokeExact(functions.CallDoubleMethodAFp, jniEnvPointer, jobject, mid, jvalues);
                    default -> {
                        isRef = true;
                        yield (long) CallObjectMethodA_MH.invokeExact(functions.CallObjectMethodAFp, jniEnvPointer, jobject, mid, jvalues);
                    }
                };
                return isRef ? new GlobalRef(this, MemorySegment.ofAddress(returnValue)) : new GlobalRef(this, new JValue(returnValue));
            }

        });
    }

    public Object jObjectToJavaObject(MemorySegment jobject) {
        return throwable(() -> {
            CallStaticMethodByName(JNIEnv.class.getDeclaredMethod("setSecret", Object.class), new JValue(jobject.address()));
            var res = jniToJava.get();
            jniToJava.remove();
            return res;
        });
    }

    public GlobalRef JavaObjectToJObject(Object o) {
        return throwable(() -> {
            setSecret(o);
            GlobalRef ref = CallStaticMethodByName(JNIEnv.class.getDeclaredMethod("getSecret"));
            jniToJava.remove();
            return ref;
        });
    }

    public GlobalRef newObject(Constructor ctr, JValue... jValues) {
        Parameter[] parameters = ctr.getParameters();
        if (parameters.length != jValues.length) {
            throw new IllegalArgumentException("jValues size not match");
        }
        String sig = Arrays.stream(parameters)
                .map(Parameter::getType)
                .map(NativeHelper::classToSig)
                .collect(Collectors.joining());
        String methodSig = STR."(\{sig})V";

        return throwable(() -> {
            MemorySegment jValuesPtr = allocator.allocateArray(JValue.jvalueLayout, jValues.length);
            for (int i = 0; i < jValues.length; i++) {
                jValuesPtr.set(ValueLayout.JAVA_LONG, i * JValue.jvalueLayout.byteSize(), jValues[i].getLong());
            }
            try (GlobalRef clsRef = FindClass(ctr.getDeclaringClass())) {
                MemorySegment mid = (MemorySegment) GetMethodID_MH.invokeExact(functions.GetMethodIDFp, jniEnvPointer, clsRef.ref(), allocator.allocateUtf8String("<init>"), allocator.allocateUtf8String(methodSig));
                MemorySegment newobject = (MemorySegment) NewObject_MH.invokeExact(functions.NewObjectAFp, jniEnvPointer, clsRef.ref(), mid, jValuesPtr);
                return new GlobalRef(this, newobject);
            }
        });
    }

    private MemorySegment initJniEnv() {
        try {
            return ((MemorySegment) GET_JNIENV_MH.invokeExact(MAIN_VM_Pointer, JNI_VERSION))
                    .reinterpret(Long.MAX_VALUE);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    private static void setSecret(Object o) {
        jniToJava.set(o);
    }

    private static Object getSecret() {
        return jniToJava.get();
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
        if (!Files.exists(Path.of(STR."\{javaHomePath}/lib/server/\{libName}"))) {
            jvmPath = STR."\{javaHomePath}/bin/server/\{libName}";
        }
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

    private String forDebug(MemorySegment jobject) {
        return jstringToCstr(ToString(jobject).ref());
    }
}
