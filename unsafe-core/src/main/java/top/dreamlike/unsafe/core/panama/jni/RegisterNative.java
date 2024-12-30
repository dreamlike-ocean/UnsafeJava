package top.dreamlike.unsafe.core.panama.jni;

import top.dreamlike.unsafe.core.panama.MasterKeyPanamaImpl;
import top.dreamlike.unsafe.core.panama.helper.GlobalRef;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class RegisterNative {
    private static final MemoryLayout JNI_NATIVE_METHOD_LAYOUT = MemoryLayout.structLayout(
            ValueLayout.ADDRESS, /*name*/
            ValueLayout.ADDRESS, /*signature*/
            ValueLayout.ADDRESS /*fnPtr*/
    );

    private static final VarHandle nameVH = JNI_NATIVE_METHOD_LAYOUT.varHandle(MemoryLayout.PathElement.groupElement(0));
    private static final VarHandle signatureVH = JNI_NATIVE_METHOD_LAYOUT.varHandle(MemoryLayout.PathElement.groupElement(1));
    private static final VarHandle fnPtrVH = JNI_NATIVE_METHOD_LAYOUT.varHandle(MemoryLayout.PathElement.groupElement(2));

    private static final MethodHandle registerNativeMH = Linker.nativeLinker()
            .downcallHandle(
                    FunctionDescriptor.of(
                            ValueLayout.JAVA_INT,
                            ValueLayout.ADDRESS /*JNIEnv *env */ ,
                            ValueLayout.ADDRESS /*jclass*/,
                            ValueLayout.ADDRESS /*method*/,
                            ValueLayout.JAVA_INT /*nMethods*/
                    )
            );

    public record MethodBinderRequest(Method source, MethodHandle target) {

    }

    public static void nativeBinder(JNIEnv jniEnv, Class clazz, List<MethodBinderRequest> methodBinderRequests) {
        MemorySegment registerNativesFp = jniEnv.functions.RegisterNativesFp;

        MethodHandles.Lookup backDoorMH = MasterKeyPanamaImpl.lookup;
        try (
                Arena arena = Arena.ofConfined();
                GlobalRef jclassRef = jniEnv.FindClass(clazz);
        ) {
            MemorySegment methods = arena.allocate(JNI_NATIVE_METHOD_LAYOUT, methodBinderRequests.size());

            int i = 0;
            for (MethodBinderRequest request : methodBinderRequests) {
                Method source = request.source;
                String name = source.getName();
                String signature = Arrays.stream(source.getParameterTypes())
                        .map(Class::descriptorString)
                        .collect(Collectors.joining("", "(", ")" + source.getReturnType().descriptorString()));
                MethodHandle handle = request.target;
                //mh (..)
                //mh(MemorySegment,MemorySegment,..)
                handle = MethodHandles.dropArguments(
                        handle, 0, MemorySegment.class, MemorySegment.class
                );
                FunctionDescriptor functionDescriptor = toFunctionDescriptor(handle.type());
                // (void* -> jnienv, void* -> jclass, ...)
                MemorySegment fnPtr = Linker.nativeLinker()
                        .upcallStub(
                                handle,
                                functionDescriptor,
                                Arena.global()
                        );
                MemorySegment namePtr = arena.allocateFrom(name);
                MemorySegment signaturePtr = arena.allocateFrom(signature);

                long offset = i * JNI_NATIVE_METHOD_LAYOUT.byteSize();
                nameVH.set(methods, offset, namePtr);
                signatureVH.set(methods, offset, signaturePtr);
                fnPtrVH.set(methods, offset, fnPtr);
                i++;
            }

            var res = (int) registerNativeMH.invokeExact(registerNativesFp, jniEnv.functions.jniEnvPointer, jclassRef.ref(), methods, methodBinderRequests.size());
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    private static FunctionDescriptor toFunctionDescriptor(MethodType methodType) {
        boolean isVoid = methodType.returnType().equals(void.class);
        Class<?>[] parameterArray = methodType.parameterArray();
        MemoryLayout[] valueLayouts = Arrays.stream(parameterArray)
                .map(RegisterNative::toLayout)
                .toArray(MemoryLayout[]::new);

        return isVoid ? FunctionDescriptor.ofVoid(valueLayouts)
                : FunctionDescriptor.of(toLayout(methodType.returnType()), valueLayouts);
    }

    private static MemoryLayout toLayout(Class<?> clazz) {
        return switch (clazz) {
            case Class c when c == int.class -> ValueLayout.JAVA_INT;
            case Class c when c == long.class -> ValueLayout.JAVA_LONG;
            case Class c when c == short.class -> ValueLayout.JAVA_SHORT;
            case Class c when c == char.class -> ValueLayout.JAVA_CHAR;
            case Class c when c == float.class -> ValueLayout.JAVA_FLOAT;
            case Class c when c == double.class -> ValueLayout.JAVA_DOUBLE;
            case Class c when c == byte.class -> ValueLayout.JAVA_FLOAT;
            case Class c when c == boolean.class -> ValueLayout.JAVA_BOOLEAN;
            case Class c when c == MemorySegment.class -> ValueLayout.ADDRESS;
            default -> throw new IllegalArgumentException("primitiveType must be a primitive type");
        };
    }

}
