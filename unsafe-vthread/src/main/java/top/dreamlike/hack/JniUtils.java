package top.dreamlike.hack;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;

public class JniUtils {

    final Arena arena;

    private final static MemorySegment mainVMPointer;

    public final MemorySegment jniEnvPointer = getJNIEnv();

    final static MethodHandle JNU_NewStringPlatformMH;

    final static MethodHandle JNU_GetStaticFieldByName_MH;

    final static MethodHandle JNU_CallStaticMethodByNameWithoutArgMH;

    final static MethodHandle JNU_CallMethodByNameWithoutArgMH;

    final static MethodHandle JNU_CallMethodByNameSingleArgMH;

    final static MethodHandle JNU_ToStringMH;

    final static MethodHandle JNU_GetStringPlatformCharMH;

    final static MethodHandle NewGlobalRefMH;

    final static MethodHandle DeleteGlobalRefMH;

    final static MethodHandle FIND_SYSTEM_CLASS_MH;

    final static MethodHandle JVM_AddModuleExportsToAll_MH;

    public static final MemorySegment JNU_CallMethodByNameFP;

    private static final MethodHandle JNU_GetEnv_MH;

    public JniUtils(Arena arena) {
        this.arena = arena;
    }

    public static MemorySegment getJNIEnv() {
        int jni_version = 0x00150000;
        try {
            return ((MemorySegment) JNU_GetEnv_MH.invokeExact(mainVMPointer, jni_version)).reinterpret(Long.MAX_VALUE);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }


    public long /*jclass*/ getSystemClass(Class c) throws Throwable {
        return (long) FIND_SYSTEM_CLASS_MH.invokeExact(
                FindClassFP,
                jniEnvPointer,
                arena.allocateUtf8String(c.getName().replace(".", "/"))
        );
    }


    public long /*jstring*/ JNU_NewStringPlatform(String str) throws Throwable {
        return (long) JNU_NewStringPlatformMH.invokeExact(
                jniEnvPointer,
                arena.allocateUtf8String(str)
        );
    }

    public void JVM_AddModuleExportsToAll_MH(Class fromClass, MemorySegment packageName) throws Throwable {
        long utilsSystemClass = getSystemClass(fromClass);
//        Main.class.getModule()
        long module = JNU_CallMethodByNameWithoutArg(utilsSystemClass, "getModule", "()Ljava/lang/Module;");
        module = NewGlobalRef(module);
        long jstring = StringToJString(packageName);
        jstring = NewGlobalRef(jstring);
        try {
            JVM_AddModuleExportsToAll_MH.invokeExact(jniEnvPointer, MemorySegment.ofAddress(module), MemorySegment.ofAddress(jstring));
        } finally {
            DeleteGlobalRef(module);
            DeleteGlobalRef(jstring);
        }
    }

    public long /*jobject*/ JNU_GetStaticFieldByName(String className, String fieldName, String sig) throws Throwable {

        return (long) JNU_GetStaticFieldByName_MH.invokeExact(
                jniEnvPointer,
                MemorySegment.NULL,
                arena.allocateUtf8String(className),
                arena.allocateUtf8String(fieldName),
                arena.allocateUtf8String(sig)
        );

    }

    public long /*jobject*/ NewGlobalRef(long jobject) throws Throwable {
        return (long) NewGlobalRefMH.invokeExact(NewGlobalRefFP, jniEnvPointer, MemorySegment.ofAddress(jobject));
    }

    public long /*jobject*/ DeleteGlobalRef(long jobject) throws Throwable {
        return (long) DeleteGlobalRefMH.invokeExact(DeleteGlobalRefFP, jniEnvPointer, MemorySegment.ofAddress(jobject));
    }


    //这里的返回值理论上应该是jvalue这个union的。。
    public long /*jobject*/ JNU_CallStaticMethodByNameWithoutArg(String className, String methodName, String sig) throws Throwable {

        return (long) JNU_CallStaticMethodByNameWithoutArgMH.invokeExact(
                jniEnvPointer,
                MemorySegment.NULL,
                arena.allocateUtf8String(className),
                arena.allocateUtf8String(methodName),
                arena.allocateUtf8String(sig)
        );
    }


    public long /*jobject*/ JNU_CallMethodByNameWithoutArg(long jobject, String methodName, String sig) throws Throwable {

        return (long) JNU_CallMethodByNameWithoutArgMH.invokeExact(
                jniEnvPointer,
                MemorySegment.NULL,
                MemorySegment.ofAddress(jobject),
                arena.allocateUtf8String(methodName),
                arena.allocateUtf8String(sig)
        );

    }

    public long /*jobject*/ JNU_CallMethodByNameSingleArgs(long jobject, String methodName, String sig, long jobjectArg0) throws Throwable {

        return (long) JNU_CallMethodByNameSingleArgMH.invokeExact(
                jniEnvPointer,
                MemorySegment.NULL,
                MemorySegment.ofAddress(jobject),
                arena.allocateUtf8String(methodName),
                arena.allocateUtf8String(sig),
                MemorySegment.ofAddress(jobjectArg0)
        );

    }

    public String jobjectToString(long jobject) throws Throwable {
        MemorySegment jstring = (MemorySegment) JNU_ToStringMH.invokeExact(jniEnvPointer, MemorySegment.ofAddress(jobject));
        MemorySegment string = (MemorySegment) JNU_GetStringPlatformCharMH.invokeExact(jniEnvPointer, jstring, MemorySegment.NULL);
        return string.reinterpret(Long.MAX_VALUE).getUtf8String(0);
    }

    public long StringToJString(MemorySegment str) throws Throwable {
        return (long) JNU_NewStringPlatformMH.invokeExact(jniEnvPointer, str);
    }


    static {
        try {
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
            mainVMPointer = vm.get(ValueLayout.ADDRESS, 0);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    static {
        try {
            MemorySegment JNU_GetEnv_FP = SymbolLookup.loaderLookup()
                    .find("JNU_GetEnv")
                    .get();
            JNU_GetEnv_MH = Linker.nativeLinker()
                    .downcallHandle(FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT))
                    .bindTo(JNU_GetEnv_FP);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    static {
        MemorySegment JNU_GetStaticFieldByName_FP = SymbolLookup.loaderLookup()
                .find("JNU_GetStaticFieldByName")
                .get();

        JNU_GetStaticFieldByName_MH = Linker.nativeLinker()
                .downcallHandle(FunctionDescriptor.of(
                        ValueLayout.JAVA_LONG,
                        /*JNIEnv *env */ValueLayout.ADDRESS,
                        /*jboolean *hasException*/ValueLayout.ADDRESS,
                        /* const char *classname*/ ValueLayout.ADDRESS,
                        /*const char *name*/ ValueLayout.ADDRESS,
                        /* const char *signature*/ ValueLayout.ADDRESS
                ))
                .bindTo(JNU_GetStaticFieldByName_FP);
    }

    static {
        MemorySegment JNU_CallStaticMethodByNameFP = SymbolLookup.loaderLookup()
                .find("JNU_CallStaticMethodByName")
                .get();

        JNU_CallStaticMethodByNameWithoutArgMH = Linker.nativeLinker()
                .downcallHandle(FunctionDescriptor.of(
                                ValueLayout.JAVA_LONG,
                                /*JNIEnv *env */ValueLayout.ADDRESS,
                                /*jboolean *hasException*/ValueLayout.ADDRESS,
                                /* const char *classname*/ ValueLayout.ADDRESS,
                                /*const char *name*/ ValueLayout.ADDRESS,
                                /* const char *signature*/ ValueLayout.ADDRESS
                        )
                ).bindTo(JNU_CallStaticMethodByNameFP);
    }

    static {
        JNU_CallMethodByNameFP = SymbolLookup.loaderLookup()
                .find("JNU_CallMethodByName")
                .get();
        JNU_CallMethodByNameWithoutArgMH = Linker.nativeLinker()
                .downcallHandle(FunctionDescriptor.of(
                                ValueLayout.JAVA_LONG,
                                /*JNIEnv *env */ValueLayout.ADDRESS,
                                /*jboolean *hasException*/ValueLayout.ADDRESS,
                                /*  jobject obj **/ ValueLayout.ADDRESS,
                                /*const char *name*/ ValueLayout.ADDRESS,
                                /* const char *signature*/ ValueLayout.ADDRESS
                        )
                ).bindTo(JNU_CallMethodByNameFP);
        JNU_CallMethodByNameSingleArgMH = Linker.nativeLinker()
                .downcallHandle(FunctionDescriptor.of(
                                ValueLayout.JAVA_LONG,
                                /*JNIEnv *env */ValueLayout.ADDRESS,
                                /*jboolean *hasException*/ValueLayout.ADDRESS,
                                /*  jobject obj **/ ValueLayout.ADDRESS,
                                /*const char *name*/ ValueLayout.ADDRESS,
                                /* const char *signature*/ ValueLayout.ADDRESS,
                                /* jstring arg0*/ ValueLayout.ADDRESS
                        )
                ).bindTo(JNU_CallMethodByNameFP);
    }

    static {
        MemorySegment JNU_NewStringPlatformFP = SymbolLookup.loaderLookup()
                .find("JNU_NewStringPlatform")
                .get();
        JNU_NewStringPlatformMH = Linker.nativeLinker()
                .downcallHandle(FunctionDescriptor.of(
                        /*jstring*/ValueLayout.JAVA_LONG,
                        /*JNIEnv *env */ValueLayout.ADDRESS,
                        /*const char *str*/ ValueLayout.ADDRESS
                )).bindTo(JNU_NewStringPlatformFP);
    }

    static {
        MemorySegment JNU_ToString_FP = SymbolLookup.loaderLookup()
                .find("JNU_ToString")
                .get();
        JNU_ToStringMH = Linker.nativeLinker()
                .downcallHandle(FunctionDescriptor.of(
                        ValueLayout.ADDRESS,
                        /*JNIEnv *env */ValueLayout.ADDRESS,
                        /*jobject obj*/ValueLayout.ADDRESS
                )).bindTo(JNU_ToString_FP);
        MemorySegment GetStringPlatformChars_FP = SymbolLookup.loaderLookup()
                .find("GetStringPlatformChars")
                .get();
        JNU_GetStringPlatformCharMH = Linker.nativeLinker()
                .downcallHandle(FunctionDescriptor.of(
                        ValueLayout.ADDRESS,
                        /*JNIEnv *env */ValueLayout.ADDRESS,
                        /*jstring jstr*/ValueLayout.ADDRESS,
                        /*jboolean *isCopy*/ ValueLayout.ADDRESS
                ))
                .bindTo(GetStringPlatformChars_FP);
    }

    static {
        MemorySegment JVM_AddModuleExportsToAll_FP = SymbolLookup.loaderLookup()
                .find("JVM_AddModuleExportsToAllUnnamed").get();
        JVM_AddModuleExportsToAll_MH = Linker.nativeLinker()
                .downcallHandle(FunctionDescriptor.ofVoid(
                        /*JNIEnv*/ ValueLayout.ADDRESS,
                        /*jobject*/ ValueLayout.ADDRESS,
                        /*jstring*/ ValueLayout.ADDRESS
                )).bindTo(JVM_AddModuleExportsToAll_FP);
    }

    private static final long ADDRESS_SIZE = ValueLayout.ADDRESS.byteSize();
    final MemorySegment functions = jniEnvPointer.get(ValueLayout.ADDRESS, 0).reinterpret(Long.MAX_VALUE);
    final MemorySegment GetVersionFP = functions.get(ValueLayout.ADDRESS, 4 * ADDRESS_SIZE); //GetVersion
    final MemorySegment DefineClassFP = functions.get(ValueLayout.ADDRESS, 5 * ADDRESS_SIZE); //DefineClass
    final MemorySegment FindClassFP = functions.get(ValueLayout.ADDRESS, 48); //FindClass
    final MemorySegment FromReflectedMethodFP = functions.get(ValueLayout.ADDRESS, 56); //FromReflectedMethod
    final MemorySegment FromReflectedFieldFP = functions.get(ValueLayout.ADDRESS, 64); //FromReflectedField
    final MemorySegment ToReflectedMethodFP = functions.get(ValueLayout.ADDRESS, 72); //ToReflectedMethod
    final MemorySegment GetSuperclassFP = functions.get(ValueLayout.ADDRESS, 80); //GetSuperclass
    final MemorySegment IsAssignableFromFP = functions.get(ValueLayout.ADDRESS, 88); //IsAssignableFrom
    final MemorySegment ToReflectedFieldFP = functions.get(ValueLayout.ADDRESS, 96); //ToReflectedField
    final MemorySegment ThrowFP = functions.get(ValueLayout.ADDRESS, 104); //Throw
    final MemorySegment ThrowNewFP = functions.get(ValueLayout.ADDRESS, 112); //ThrowNew
    final MemorySegment ExceptionOccurredFP = functions.get(ValueLayout.ADDRESS, 120); //ExceptionOccurred
    final MemorySegment ExceptionDescribeFP = functions.get(ValueLayout.ADDRESS, 128); //ExceptionDescribe
    final MemorySegment ExceptionClearFP = functions.get(ValueLayout.ADDRESS, 136); //ExceptionClear
    final MemorySegment FatalErrorFP = functions.get(ValueLayout.ADDRESS, 144); //FatalError
    final MemorySegment PushLocalFrameFP = functions.get(ValueLayout.ADDRESS, 152); //PushLocalFrame
    final MemorySegment PopLocalFrameFP = functions.get(ValueLayout.ADDRESS, 160); //PopLocalFrame
    final MemorySegment NewGlobalRefFP = functions.get(ValueLayout.ADDRESS, 168); //NewGlobalRef
    final MemorySegment DeleteGlobalRefFP = functions.get(ValueLayout.ADDRESS, 176); //DeleteGlobalRef
    final MemorySegment DeleteLocalRefFP = functions.get(ValueLayout.ADDRESS, 184); //DeleteLocalRef
    final MemorySegment IsSameObjectFP = functions.get(ValueLayout.ADDRESS, 192); //IsSameObject
    final MemorySegment NewLocalRefFP = functions.get(ValueLayout.ADDRESS, 200); //NewLocalRef
    final MemorySegment EnsureLocalCapacityFP = functions.get(ValueLayout.ADDRESS, 208); //EnsureLocalCapacity
    final MemorySegment AllocObjectFP = functions.get(ValueLayout.ADDRESS, 216); //AllocObject
    final MemorySegment NewObjectFP = functions.get(ValueLayout.ADDRESS, 224); //NewObject
    final MemorySegment NewObjectVFP = functions.get(ValueLayout.ADDRESS, 232); //NewObjectV
    final MemorySegment NewObjectAFP = functions.get(ValueLayout.ADDRESS, 240); //NewObjectA
    final MemorySegment GetObjectClassFP = functions.get(ValueLayout.ADDRESS, 248); //GetObjectClass
    final MemorySegment IsInstanceOfFP = functions.get(ValueLayout.ADDRESS, 256); //IsInstanceOf
    final MemorySegment GetMethodIDFP = functions.get(ValueLayout.ADDRESS, 264); //GetMethodID
    final MemorySegment CallObjectMethodFP = functions.get(ValueLayout.ADDRESS, 272); //CallObjectMethod
    final MemorySegment CallObjectMethodVFP = functions.get(ValueLayout.ADDRESS, 280); //CallObjectMethodV
    final MemorySegment CallObjectMethodAFP = functions.get(ValueLayout.ADDRESS, 288); //CallObjectMethodA
    final MemorySegment CallBooleanMethodFP = functions.get(ValueLayout.ADDRESS, 296); //CallBooleanMethod
    final MemorySegment CallBooleanMethodVFP = functions.get(ValueLayout.ADDRESS, 304); //CallBooleanMethodV
    final MemorySegment CallBooleanMethodAFP = functions.get(ValueLayout.ADDRESS, 312); //CallBooleanMethodA
    final MemorySegment CallByteMethodFP = functions.get(ValueLayout.ADDRESS, 320); //CallByteMethod
    final MemorySegment CallByteMethodVFP = functions.get(ValueLayout.ADDRESS, 328); //CallByteMethodV
    final MemorySegment CallByteMethodAFP = functions.get(ValueLayout.ADDRESS, 336); //CallByteMethodA
    final MemorySegment CallCharMethodFP = functions.get(ValueLayout.ADDRESS, 344); //CallCharMethod
    final MemorySegment CallCharMethodVFP = functions.get(ValueLayout.ADDRESS, 352); //CallCharMethodV
    final MemorySegment CallCharMethodAFP = functions.get(ValueLayout.ADDRESS, 360); //CallCharMethodA
    final MemorySegment CallShortMethodFP = functions.get(ValueLayout.ADDRESS, 368); //CallShortMethod
    final MemorySegment CallShortMethodVFP = functions.get(ValueLayout.ADDRESS, 376); //CallShortMethodV
    final MemorySegment CallShortMethodAFP = functions.get(ValueLayout.ADDRESS, 384); //CallShortMethodA
    final MemorySegment CallIntMethodFP = functions.get(ValueLayout.ADDRESS, 392); //CallIntMethod
    final MemorySegment CallIntMethodVFP = functions.get(ValueLayout.ADDRESS, 400); //CallIntMethodV
    final MemorySegment CallIntMethodAFP = functions.get(ValueLayout.ADDRESS, 408); //CallIntMethodA
    final MemorySegment CallLongMethodFP = functions.get(ValueLayout.ADDRESS, 416); //CallLongMethod
    final MemorySegment CallLongMethodVFP = functions.get(ValueLayout.ADDRESS, 424); //CallLongMethodV
    final MemorySegment CallLongMethodAFP = functions.get(ValueLayout.ADDRESS, 432); //CallLongMethodA
    final MemorySegment CallFloatMethodFP = functions.get(ValueLayout.ADDRESS, 440); //CallFloatMethod
    final MemorySegment CallFloatMethodVFP = functions.get(ValueLayout.ADDRESS, 448); //CallFloatMethodV
    final MemorySegment CallFloatMethodAFP = functions.get(ValueLayout.ADDRESS, 456); //CallFloatMethodA
    final MemorySegment CallDoubleMethodFP = functions.get(ValueLayout.ADDRESS, 464); //CallDoubleMethod
    final MemorySegment CallDoubleMethodVFP = functions.get(ValueLayout.ADDRESS, 472); //CallDoubleMethodV
    final MemorySegment CallDoubleMethodAFP = functions.get(ValueLayout.ADDRESS, 480); //CallDoubleMethodA
    final MemorySegment CallVoidMethodFP = functions.get(ValueLayout.ADDRESS, 488); //CallVoidMethod
    final MemorySegment CallVoidMethodVFP = functions.get(ValueLayout.ADDRESS, 496); //CallVoidMethodV
    final MemorySegment CallVoidMethodAFP = functions.get(ValueLayout.ADDRESS, 504); //CallVoidMethodA
    final MemorySegment CallNonvirtualObjectMethodFP = functions.get(ValueLayout.ADDRESS, 512); //CallNonvirtualObjectMethod
    final MemorySegment CallNonvirtualObjectMethodVFP = functions.get(ValueLayout.ADDRESS, 520); //CallNonvirtualObjectMethodV
    final MemorySegment CallNonvirtualObjectMethodAFP = functions.get(ValueLayout.ADDRESS, 528); //CallNonvirtualObjectMethodA
    final MemorySegment CallNonvirtualBooleanMethodFP = functions.get(ValueLayout.ADDRESS, 536); //CallNonvirtualBooleanMethod
    final MemorySegment CallNonvirtualBooleanMethodVFP = functions.get(ValueLayout.ADDRESS, 544); //CallNonvirtualBooleanMethodV
    final MemorySegment CallNonvirtualBooleanMethodAFP = functions.get(ValueLayout.ADDRESS, 552); //CallNonvirtualBooleanMethodA
    final MemorySegment CallNonvirtualByteMethodFP = functions.get(ValueLayout.ADDRESS, 560); //CallNonvirtualByteMethod
    final MemorySegment CallNonvirtualByteMethodVFP = functions.get(ValueLayout.ADDRESS, 568); //CallNonvirtualByteMethodV
    final MemorySegment CallNonvirtualByteMethodAFP = functions.get(ValueLayout.ADDRESS, 576); //CallNonvirtualByteMethodA
    final MemorySegment CallNonvirtualCharMethodFP = functions.get(ValueLayout.ADDRESS, 584); //CallNonvirtualCharMethod
    final MemorySegment CallNonvirtualCharMethodVFP = functions.get(ValueLayout.ADDRESS, 592); //CallNonvirtualCharMethodV
    final MemorySegment CallNonvirtualCharMethodAFP = functions.get(ValueLayout.ADDRESS, 600); //CallNonvirtualCharMethodA
    final MemorySegment CallNonvirtualShortMethodFP = functions.get(ValueLayout.ADDRESS, 608); //CallNonvirtualShortMethod
    final MemorySegment CallNonvirtualShortMethodVFP = functions.get(ValueLayout.ADDRESS, 616); //CallNonvirtualShortMethodV
    final MemorySegment CallNonvirtualShortMethodAFP = functions.get(ValueLayout.ADDRESS, 624); //CallNonvirtualShortMethodA
    final MemorySegment CallNonvirtualIntMethodFP = functions.get(ValueLayout.ADDRESS, 632); //CallNonvirtualIntMethod
    final MemorySegment CallNonvirtualIntMethodVFP = functions.get(ValueLayout.ADDRESS, 640); //CallNonvirtualIntMethodV
    final MemorySegment CallNonvirtualIntMethodAFP = functions.get(ValueLayout.ADDRESS, 648); //CallNonvirtualIntMethodA
    final MemorySegment CallNonvirtualLongMethodFP = functions.get(ValueLayout.ADDRESS, 656); //CallNonvirtualLongMethod
    final MemorySegment CallNonvirtualLongMethodVFP = functions.get(ValueLayout.ADDRESS, 664); //CallNonvirtualLongMethodV
    final MemorySegment CallNonvirtualLongMethodAFP = functions.get(ValueLayout.ADDRESS, 672); //CallNonvirtualLongMethodA
    final MemorySegment CallNonvirtualFloatMethodFP = functions.get(ValueLayout.ADDRESS, 680); //CallNonvirtualFloatMethod
    final MemorySegment CallNonvirtualFloatMethodVFP = functions.get(ValueLayout.ADDRESS, 688); //CallNonvirtualFloatMethodV
    final MemorySegment CallNonvirtualFloatMethodAFP = functions.get(ValueLayout.ADDRESS, 696); //CallNonvirtualFloatMethodA
    final MemorySegment CallNonvirtualDoubleMethodFP = functions.get(ValueLayout.ADDRESS, 704); //CallNonvirtualDoubleMethod
    final MemorySegment CallNonvirtualDoubleMethodVFP = functions.get(ValueLayout.ADDRESS, 712); //CallNonvirtualDoubleMethodV
    final MemorySegment CallNonvirtualDoubleMethodAFP = functions.get(ValueLayout.ADDRESS, 720); //CallNonvirtualDoubleMethodA
    final MemorySegment CallNonvirtualVoidMethodFP = functions.get(ValueLayout.ADDRESS, 728); //CallNonvirtualVoidMethod
    final MemorySegment CallNonvirtualVoidMethodVFP = functions.get(ValueLayout.ADDRESS, 736); //CallNonvirtualVoidMethodV
    final MemorySegment CallNonvirtualVoidMethodAFP = functions.get(ValueLayout.ADDRESS, 744); //CallNonvirtualVoidMethodA
    final MemorySegment GetFieldIDFP = functions.get(ValueLayout.ADDRESS, 752); //GetFieldID
    final MemorySegment GetObjectFieldFP = functions.get(ValueLayout.ADDRESS, 760); //GetObjectField


    static {
        NewGlobalRefMH = Linker.nativeLinker()
                .downcallHandle(FunctionDescriptor.of(
                        ValueLayout.JAVA_LONG,
                        /*JNIEnv *env */ValueLayout.ADDRESS,
                        /*jobject obj*/ValueLayout.ADDRESS
                ));
        DeleteGlobalRefMH = Linker.nativeLinker()
                .downcallHandle(FunctionDescriptor.of(
                        ValueLayout.JAVA_LONG,
                        /*JNIEnv *env */ValueLayout.ADDRESS,
                        /*jobject obj*/ValueLayout.ADDRESS
                ));
    }


    static {
        FIND_SYSTEM_CLASS_MH = Linker.nativeLinker()
                .downcallHandle(FunctionDescriptor.of(
                        ValueLayout.JAVA_LONG,
                        /*JNIEnv *env */ValueLayout.ADDRESS,
                        /*const char *name*/ ValueLayout.ADDRESS
                ));
    }

}
