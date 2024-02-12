package top.dreamlike;

import top.dreamlike.hack.JniUtils;

import java.io.IOException;
import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class Main {

    private static long a = 20210214;

    public static MethodHandles.Lookup IMPL_LOOKUP;

    public static void main(String[] args) throws Throwable {
        List<String> allJniEnvFnName = Files.readAllLines(Path.of("t.txt"))
                .stream()
                .filter(Predicate.not(String::isBlank))
                .map(s -> {
                    int start = s.indexOf("JNICALL *");
                    if (start == -1) {
                        return null;
                    }
                    start += "JNICALL *".length();
                    int end = s.indexOf(")", start);
                    return s.substring(start, end);
                })
                .filter(Predicate.not(Objects::isNull))
                //MemorySegment CallBooleanMethodFp = functions.get(ValueLayout.ADDRESS, ADDRESS_SIZE * i++);
                .toList();
        String fields = allJniEnvFnName.stream()
                .map(s -> STR."final MemorySegment \{s}Fp;")
                .collect(Collectors.joining("\n"));
        String ctorInit = allJniEnvFnName.stream()
                .map(s -> STR."\{s}Fp = functions.get(ValueLayout.ADDRESS, ADDRESS_SIZE * i++);")
                .collect(Collectors.joining("\n"));

        System.out.println(fields);
        System.out.println(ctorInit);

//        Arena tmp = Arena.global();
//        Field field = MethodHandles.Lookup.class.getDeclaredField("IMPL_LOOKUP");
//        field.setAccessible(true);
//        Object o = field.get(null);
//
//
//        JniUtils jniUtils = new JniUtils(tmp);
//
////        jniUtils.JVM_AddModuleExportsToAll_MH(MethodHandles.Lookup.class, tmp.allocateUtf8String("java.lang.invoke"));
//
//        long javaLangAccess = jniUtils.JNU_GetStaticFieldByName("jdk/internal/access/SharedSecrets", "javaLangAccess", "Ljdk/internal/access/JavaLangAccess;");
//        javaLangAccess = jniUtils.NewGlobalRef(javaLangAccess);
//
//        MethodHandle addExport = Linker.nativeLinker()
//                .downcallHandle(FunctionDescriptor.of(
//                        ValueLayout.ADDRESS,
//                        /*JNIEnv *env */ValueLayout.ADDRESS,
//                        /*jboolean *hasException*/ValueLayout.ADDRESS,
//                        /*  jobject obj **/ ValueLayout.ADDRESS,
//                        /*const char *name*/ ValueLayout.ADDRESS,
//                        /* const char *signature*/ ValueLayout.ADDRESS,
//                        /* jobject Module*/ ValueLayout.ADDRESS,
//                        /* jstring pkg*/ ValueLayout.ADDRESS
//                )).bindTo(JniUtils.JNU_CallMethodByNameFP);
//
//        long utilsSystemClass = jniUtils.getSystemClass(MethodHandles.Lookup.class);
//        long module = jniUtils.JNU_CallMethodByNameWithoutArg(utilsSystemClass, "getModule", "()Ljava/lang/Module;");
//        module = jniUtils.NewGlobalRef(module);
//
//        long pkg = jniUtils.StringToJString(tmp.allocateUtf8String("java.lang.invoke"));
//        pkg = jniUtils.NewGlobalRef(pkg);
//        MemorySegment address = (MemorySegment) addExport.invokeExact(
//                jniUtils.jniEnvPointer, MemorySegment.NULL, MemorySegment.ofAddress(javaLangAccess), tmp.allocateUtf8String("addOpensToAllUnnamed"),
//                tmp.allocateUtf8String("(Ljava/lang/Module;Ljava/lang/String;)V"), MemorySegment.ofAddress(module), MemorySegment.ofAddress(pkg));
//
//        System.out.println(o);


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