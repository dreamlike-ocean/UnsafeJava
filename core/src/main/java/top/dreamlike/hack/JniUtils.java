package top.dreamlike.hack;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;

public class JniUtils {

    final Arena arena;

    private final static MemorySegment mainVMPointer;

    public final static MemorySegment jniEnvPointer;

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

    public JniUtils(Arena arena) {
        this.arena = arena;
    }


    public long /*jclass*/ getSystemClass(Class c) throws Throwable {
        return (long) FIND_SYSTEM_CLASS_MH.invokeExact(
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
        return (long) NewGlobalRefMH.invokeExact(jniEnvPointer, MemorySegment.ofAddress(jobject));
    }

    public long /*jobject*/ DeleteGlobalRef(long jobject) throws Throwable {
        return (long) DeleteGlobalRefMH.invokeExact(jniEnvPointer, MemorySegment.ofAddress(jobject));
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
            String jvmPath = "/home/dreamlike/jdks/jdk/build/linux-x86_64-server-slowdebug/jdk/lib/server/libjvm.so";
//            String jvmPath = "/home/dreamlike/jdks/jdk-21.0.1/lib/server/libjvm.so";
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
            int jni_version = 0x00150000;
            MethodHandle JNU_GetEnv_MH = Linker.nativeLinker()
                    .downcallHandle(FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT))
                    .bindTo(JNU_GetEnv_FP);

            MemorySegment jni_address = (MemorySegment) JNU_GetEnv_MH.invokeExact(mainVMPointer, jni_version);
            jniEnvPointer = jni_address.reinterpret(Long.MAX_VALUE);
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

    static final MemorySegment functions = jniEnvPointer.get(ValueLayout.ADDRESS, 0).reinterpret(Long.MAX_VALUE);
    static final MemorySegment GetVersionFP = functions.get(ValueLayout.ADDRESS, 32); //GetVersion
    static final MemorySegment DefineClassFP = functions.get(ValueLayout.ADDRESS, 40); //DefineClass
    static final MemorySegment FindClassFP = functions.get(ValueLayout.ADDRESS, 48); //FindClass
    static final MemorySegment FromReflectedMethodFP = functions.get(ValueLayout.ADDRESS, 56); //FromReflectedMethod
    static final MemorySegment FromReflectedFieldFP = functions.get(ValueLayout.ADDRESS, 64); //FromReflectedField
    static final MemorySegment ToReflectedMethodFP = functions.get(ValueLayout.ADDRESS, 72); //ToReflectedMethod
    static final MemorySegment GetSuperclassFP = functions.get(ValueLayout.ADDRESS, 80); //GetSuperclass
    static final MemorySegment IsAssignableFromFP = functions.get(ValueLayout.ADDRESS, 88); //IsAssignableFrom
    static final MemorySegment ToReflectedFieldFP = functions.get(ValueLayout.ADDRESS, 96); //ToReflectedField
    static final MemorySegment ThrowFP = functions.get(ValueLayout.ADDRESS, 104); //Throw
    static final MemorySegment ThrowNewFP = functions.get(ValueLayout.ADDRESS, 112); //ThrowNew
    static final MemorySegment ExceptionOccurredFP = functions.get(ValueLayout.ADDRESS, 120); //ExceptionOccurred
    static final MemorySegment ExceptionDescribeFP = functions.get(ValueLayout.ADDRESS, 128); //ExceptionDescribe
    static final MemorySegment ExceptionClearFP = functions.get(ValueLayout.ADDRESS, 136); //ExceptionClear
    static final MemorySegment FatalErrorFP = functions.get(ValueLayout.ADDRESS, 144); //FatalError
    static final MemorySegment PushLocalFrameFP = functions.get(ValueLayout.ADDRESS, 152); //PushLocalFrame
    static final MemorySegment PopLocalFrameFP = functions.get(ValueLayout.ADDRESS, 160); //PopLocalFrame
    static final MemorySegment NewGlobalRefFP = functions.get(ValueLayout.ADDRESS, 168); //NewGlobalRef
    static final MemorySegment DeleteGlobalRefFP = functions.get(ValueLayout.ADDRESS, 176); //DeleteGlobalRef
    static final MemorySegment DeleteLocalRefFP = functions.get(ValueLayout.ADDRESS, 184); //DeleteLocalRef
    static final MemorySegment IsSameObjectFP = functions.get(ValueLayout.ADDRESS, 192); //IsSameObject
    static final MemorySegment NewLocalRefFP = functions.get(ValueLayout.ADDRESS, 200); //NewLocalRef
    static final MemorySegment EnsureLocalCapacityFP = functions.get(ValueLayout.ADDRESS, 208); //EnsureLocalCapacity
    static final MemorySegment AllocObjectFP = functions.get(ValueLayout.ADDRESS, 216); //AllocObject
    static final MemorySegment NewObjectFP = functions.get(ValueLayout.ADDRESS, 224); //NewObject
    static final MemorySegment NewObjectVFP = functions.get(ValueLayout.ADDRESS, 232); //NewObjectV
    static final MemorySegment NewObjectAFP = functions.get(ValueLayout.ADDRESS, 240); //NewObjectA
    static final MemorySegment GetObjectClassFP = functions.get(ValueLayout.ADDRESS, 248); //GetObjectClass
    static final MemorySegment IsInstanceOfFP = functions.get(ValueLayout.ADDRESS, 256); //IsInstanceOf
    static final MemorySegment GetMethodIDFP = functions.get(ValueLayout.ADDRESS, 264); //GetMethodID
    static final MemorySegment CallObjectMethodFP = functions.get(ValueLayout.ADDRESS, 272); //CallObjectMethod
    static final MemorySegment CallObjectMethodVFP = functions.get(ValueLayout.ADDRESS, 280); //CallObjectMethodV
    static final MemorySegment CallObjectMethodAFP = functions.get(ValueLayout.ADDRESS, 288); //CallObjectMethodA
    static final MemorySegment CallBooleanMethodFP = functions.get(ValueLayout.ADDRESS, 296); //CallBooleanMethod
    static final MemorySegment CallBooleanMethodVFP = functions.get(ValueLayout.ADDRESS, 304); //CallBooleanMethodV
    static final MemorySegment CallBooleanMethodAFP = functions.get(ValueLayout.ADDRESS, 312); //CallBooleanMethodA
    static final MemorySegment CallByteMethodFP = functions.get(ValueLayout.ADDRESS, 320); //CallByteMethod
    static final MemorySegment CallByteMethodVFP = functions.get(ValueLayout.ADDRESS, 328); //CallByteMethodV
    static final MemorySegment CallByteMethodAFP = functions.get(ValueLayout.ADDRESS, 336); //CallByteMethodA
    static final MemorySegment CallCharMethodFP = functions.get(ValueLayout.ADDRESS, 344); //CallCharMethod
    static final MemorySegment CallCharMethodVFP = functions.get(ValueLayout.ADDRESS, 352); //CallCharMethodV
    static final MemorySegment CallCharMethodAFP = functions.get(ValueLayout.ADDRESS, 360); //CallCharMethodA
    static final MemorySegment CallShortMethodFP = functions.get(ValueLayout.ADDRESS, 368); //CallShortMethod
    static final MemorySegment CallShortMethodVFP = functions.get(ValueLayout.ADDRESS, 376); //CallShortMethodV
    static final MemorySegment CallShortMethodAFP = functions.get(ValueLayout.ADDRESS, 384); //CallShortMethodA
    static final MemorySegment CallIntMethodFP = functions.get(ValueLayout.ADDRESS, 392); //CallIntMethod
    static final MemorySegment CallIntMethodVFP = functions.get(ValueLayout.ADDRESS, 400); //CallIntMethodV
    static final MemorySegment CallIntMethodAFP = functions.get(ValueLayout.ADDRESS, 408); //CallIntMethodA
    static final MemorySegment CallLongMethodFP = functions.get(ValueLayout.ADDRESS, 416); //CallLongMethod
    static final MemorySegment CallLongMethodVFP = functions.get(ValueLayout.ADDRESS, 424); //CallLongMethodV
    static final MemorySegment CallLongMethodAFP = functions.get(ValueLayout.ADDRESS, 432); //CallLongMethodA
    static final MemorySegment CallFloatMethodFP = functions.get(ValueLayout.ADDRESS, 440); //CallFloatMethod
    static final MemorySegment CallFloatMethodVFP = functions.get(ValueLayout.ADDRESS, 448); //CallFloatMethodV
    static final MemorySegment CallFloatMethodAFP = functions.get(ValueLayout.ADDRESS, 456); //CallFloatMethodA
    static final MemorySegment CallDoubleMethodFP = functions.get(ValueLayout.ADDRESS, 464); //CallDoubleMethod
    static final MemorySegment CallDoubleMethodVFP = functions.get(ValueLayout.ADDRESS, 472); //CallDoubleMethodV
    static final MemorySegment CallDoubleMethodAFP = functions.get(ValueLayout.ADDRESS, 480); //CallDoubleMethodA
    static final MemorySegment CallVoidMethodFP = functions.get(ValueLayout.ADDRESS, 488); //CallVoidMethod
    static final MemorySegment CallVoidMethodVFP = functions.get(ValueLayout.ADDRESS, 496); //CallVoidMethodV
    static final MemorySegment CallVoidMethodAFP = functions.get(ValueLayout.ADDRESS, 504); //CallVoidMethodA
    static final MemorySegment CallNonvirtualObjectMethodFP = functions.get(ValueLayout.ADDRESS, 512); //CallNonvirtualObjectMethod
    static final MemorySegment CallNonvirtualObjectMethodVFP = functions.get(ValueLayout.ADDRESS, 520); //CallNonvirtualObjectMethodV
    static final MemorySegment CallNonvirtualObjectMethodAFP = functions.get(ValueLayout.ADDRESS, 528); //CallNonvirtualObjectMethodA
    static final MemorySegment CallNonvirtualBooleanMethodFP = functions.get(ValueLayout.ADDRESS, 536); //CallNonvirtualBooleanMethod
    static final MemorySegment CallNonvirtualBooleanMethodVFP = functions.get(ValueLayout.ADDRESS, 544); //CallNonvirtualBooleanMethodV
    static final MemorySegment CallNonvirtualBooleanMethodAFP = functions.get(ValueLayout.ADDRESS, 552); //CallNonvirtualBooleanMethodA
    static final MemorySegment CallNonvirtualByteMethodFP = functions.get(ValueLayout.ADDRESS, 560); //CallNonvirtualByteMethod
    static final MemorySegment CallNonvirtualByteMethodVFP = functions.get(ValueLayout.ADDRESS, 568); //CallNonvirtualByteMethodV
    static final MemorySegment CallNonvirtualByteMethodAFP = functions.get(ValueLayout.ADDRESS, 576); //CallNonvirtualByteMethodA
    static final MemorySegment CallNonvirtualCharMethodFP = functions.get(ValueLayout.ADDRESS, 584); //CallNonvirtualCharMethod
    static final MemorySegment CallNonvirtualCharMethodVFP = functions.get(ValueLayout.ADDRESS, 592); //CallNonvirtualCharMethodV
    static final MemorySegment CallNonvirtualCharMethodAFP = functions.get(ValueLayout.ADDRESS, 600); //CallNonvirtualCharMethodA
    static final MemorySegment CallNonvirtualShortMethodFP = functions.get(ValueLayout.ADDRESS, 608); //CallNonvirtualShortMethod
    static final MemorySegment CallNonvirtualShortMethodVFP = functions.get(ValueLayout.ADDRESS, 616); //CallNonvirtualShortMethodV
    static final MemorySegment CallNonvirtualShortMethodAFP = functions.get(ValueLayout.ADDRESS, 624); //CallNonvirtualShortMethodA
    static final MemorySegment CallNonvirtualIntMethodFP = functions.get(ValueLayout.ADDRESS, 632); //CallNonvirtualIntMethod
    static final MemorySegment CallNonvirtualIntMethodVFP = functions.get(ValueLayout.ADDRESS, 640); //CallNonvirtualIntMethodV
    static final MemorySegment CallNonvirtualIntMethodAFP = functions.get(ValueLayout.ADDRESS, 648); //CallNonvirtualIntMethodA
    static final MemorySegment CallNonvirtualLongMethodFP = functions.get(ValueLayout.ADDRESS, 656); //CallNonvirtualLongMethod
    static final MemorySegment CallNonvirtualLongMethodVFP = functions.get(ValueLayout.ADDRESS, 664); //CallNonvirtualLongMethodV
    static final MemorySegment CallNonvirtualLongMethodAFP = functions.get(ValueLayout.ADDRESS, 672); //CallNonvirtualLongMethodA
    static final MemorySegment CallNonvirtualFloatMethodFP = functions.get(ValueLayout.ADDRESS, 680); //CallNonvirtualFloatMethod
    static final MemorySegment CallNonvirtualFloatMethodVFP = functions.get(ValueLayout.ADDRESS, 688); //CallNonvirtualFloatMethodV
    static final MemorySegment CallNonvirtualFloatMethodAFP = functions.get(ValueLayout.ADDRESS, 696); //CallNonvirtualFloatMethodA
    static final MemorySegment CallNonvirtualDoubleMethodFP = functions.get(ValueLayout.ADDRESS, 704); //CallNonvirtualDoubleMethod
    static final MemorySegment CallNonvirtualDoubleMethodVFP = functions.get(ValueLayout.ADDRESS, 712); //CallNonvirtualDoubleMethodV
    static final MemorySegment CallNonvirtualDoubleMethodAFP = functions.get(ValueLayout.ADDRESS, 720); //CallNonvirtualDoubleMethodA
    static final MemorySegment CallNonvirtualVoidMethodFP = functions.get(ValueLayout.ADDRESS, 728); //CallNonvirtualVoidMethod
    static final MemorySegment CallNonvirtualVoidMethodVFP = functions.get(ValueLayout.ADDRESS, 736); //CallNonvirtualVoidMethodV
    static final MemorySegment CallNonvirtualVoidMethodAFP = functions.get(ValueLayout.ADDRESS, 744); //CallNonvirtualVoidMethodA
    static final MemorySegment GetFieldIDFP = functions.get(ValueLayout.ADDRESS, 752); //GetFieldID
    static final MemorySegment GetObjectFieldFP = functions.get(ValueLayout.ADDRESS, 760); //GetObjectField


    static {
        NewGlobalRefMH = Linker.nativeLinker()
                .downcallHandle(FunctionDescriptor.of(
                        ValueLayout.JAVA_LONG,
                        /*JNIEnv *env */ValueLayout.ADDRESS,
                        /*jobject obj*/ValueLayout.ADDRESS
                )).bindTo(NewGlobalRefFP);
        DeleteGlobalRefMH = Linker.nativeLinker()
                .downcallHandle(FunctionDescriptor.of(
                        ValueLayout.JAVA_LONG,
                        /*JNIEnv *env */ValueLayout.ADDRESS,
                        /*jobject obj*/ValueLayout.ADDRESS
                )).bindTo(DeleteGlobalRefFP);
    }


    static {
        FIND_SYSTEM_CLASS_MH = Linker.nativeLinker()
                .downcallHandle(FunctionDescriptor.of(
                        ValueLayout.JAVA_LONG,
                        /*JNIEnv *env */ValueLayout.ADDRESS,
                        /*const char *name*/ ValueLayout.ADDRESS
                )).bindTo(FindClassFP);

    }

}
