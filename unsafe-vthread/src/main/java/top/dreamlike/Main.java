package top.dreamlike;

import java.lang.invoke.MethodHandles;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static java.lang.StringTemplate.STR;

public class Main {

    private static long a = 20210214;

    public static MethodHandles.Lookup IMPL_LOOKUP;

    public static void main(String[] args) throws Throwable {
        ExecutorService service = Executors.newSingleThreadExecutor(r ->
                new Thread(r, "dreamlike-jni-hack-VirtualThread")
        );
        Thread.Builder.OfVirtual virtual = VirtualThreadUnsafe.VIRTUAL_THREAD_BUILDER.apply(service);
        CarrierThreadLocal<String> local = new CarrierThreadLocal<>();
        service.submit(() -> {
            local.set("hello vthread");
            virtual.start(() -> {
                System.out.println(STR."\{local.get()} \{Thread.currentThread()}");
            });
        });

    }

//
//JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *pjvm, void *reserved) {
//        gJvm = pjvm;  // cache the JavaVM pointer
//        auto env = getEnv();
//        //replace with one of your classes in the line below
//        auto randomClass = env->FindClass("com/example/RandomClass");
//        jclass classClass = env->GetObjectClass(randomClass);
//        auto classLoaderClass = env->FindClass("java/lang/ClassLoader");
//        auto getClassLoaderMethod = env->GetMethodID(classClass, "getClassLoader",
//                "()Ljava/lang/ClassLoader;");
//        gClassLoader = env->CallObjectMethod(randomClass, getClassLoaderMethod);
//        gFindClassMethod = env->GetMethodID(classLoaderClass, "findClass",
//                "(Ljava/lang/String;)Ljava/lang/Class;");
//
//        return JNI_VERSION_1_6;
//    }
//
//    jclass findClass(const char* name) {
//        return static_cast<jclass>(getEnv()->CallObjectMethod(gClassLoader, gFindClassMethod, getEnv()->NewStringUTF(name)));
//    }


    //findClass from jni
    //MethodHandle handle = Linker.nativeLinker()
    //                .downcallHandle(FunctionDescriptor.of(
    //                        ValueLayout.ADDRESS,
    //                        /*JNIEnv *env */ValueLayout.ADDRESS,
    //                        /*const char *name */ValueLayout.ADDRESS
    //                )).bindTo(FindClassFP);
    //        MemorySegment segment = (MemorySegment) handle.invokeExact(jni_address, global.allocateUtf8String("top/dreamlike/Main"));
    //        System.out.println(segment);


    //throwException
    //
//    MemorySegment JNU_NewObjectByNameFP = SymbolLookup.loaderLookup()
//            .find("JNU_NewObjectByName").get();
//
//    MethodHandle JNU_NewObjectByNameMH = Linker.nativeLinker()
//            .downcallHandle(FunctionDescriptor.of(
//                    ValueLayout.ADDRESS,
//                    /*JNIEnv *env */ValueLayout.ADDRESS,
//                    /*className*/ ValueLayout.ADDRESS,
//                    /*constructor_sig*/ValueLayout.ADDRESS,
//                    /*firstArg*/ ValueLayout.ADDRESS
//            )).bindTo(JNU_NewObjectByNameFP);
//
//    MemorySegment newException = (MemorySegment) JNU_NewObjectByNameMH.invokeExact(jni_address,
//            global.allocateUtf8String("sun/security/validator/ValidatorException"),
//            global.allocateUtf8String("(Ljava/lang/Object;)V"),
//            MemorySegment.ofAddress(staticValue)
//    );
//
//    MethodHandle throwMH = Linker.nativeLinker()
//            .downcallHandle(FunctionDescriptor.of(
//                    ValueLayout.ADDRESS,
//                    /*JNIEnv *env */ValueLayout.ADDRESS,
//                    /*jthrowable  */ValueLayout.ADDRESS
//            )).bindTo(ThrowFP);
//      try {
//        MemorySegment segment = (MemorySegment) throwMH.invokeExact(jni_address, newException);
//        System.out.println(segment);
//    }catch (Throwable t) {
//        t.printStackTrace();
//    }


    //
}