package io.github.dreamlike.unsafe.vthread;


import top.dreamlike.unsafe.core.MasterKey;

import java.lang.classfile.ClassFile;
import java.lang.classfile.TypeKind;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodTypeDesc;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Paths;

public class TerminatingThreadLocal<T> extends ThreadLocal<T> {

    private final static MethodHandle internalConstructorMH;

    private final ThreadLocal<T> internal;

    public TerminatingThreadLocal() {
        try {
            CallBack<T> callBack = this::threadTerminated;
            internal = (ThreadLocal<T>) internalConstructorMH.invoke(callBack);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public T get() {
        return internal.get();
    }

    @Override
    public void set(T value) {
        internal.set(value);
    }

    @Override
    public void remove() {
        internal.remove();
    }

    protected void threadTerminated(T value) {

    }


    static {
        var customerTerminatingThreadLocalClass = init();

        try {
            internalConstructorMH = MasterKey.INSTANCE.openTheDoor(customerTerminatingThreadLocalClass.getDeclaredConstructor(CallBack.class));
        }catch (Throwable throwable) {
            throw new RuntimeException(throwable);
        }
    }

    private static Class init() {
       try {
           String className = "jdk.internal.misc.TerminatingThreadLocal";
           Class<?> threadLocalClass = Class.forName(className);
           Module module = threadLocalClass.getModule();
           //需要一个内部的后门打开这个模块。。。太hack了 要不没法load我搓出来的类
           Field accessField = Class.forName("jdk.internal.access.SharedSecrets")
                   .getDeclaredField("javaLangAccess");
           Object o = VirtualThreadUnsafe
                   .IMPL_LOOKUP
                   .unreflectVarHandle(accessField)
                   .get();
           //void addExports(Module m1, String pkg);
           Method addExports = Class.forName("jdk.internal.access.JavaLangAccess")
                   .getDeclaredMethod("addExports", Module.class, String.class);
           //无条件打开java.base 的 jdk.internal.misc
           VirtualThreadUnsafe.IMPL_LOOKUP
                   .unreflect(addExports)
                   .invoke(o, module, "jdk.internal.misc");


           ClassFile classFile = ClassFile.of();
           ClassDesc thisClassDesc = ClassDesc.of("io.github.dreamlike.unsafe.vthread.CustomerTerminatingThreadLocal");
           byte[] bytes = classFile.build(thisClassDesc, it -> {
               it.withField("callback", CallBack.class.describeConstable().get(), Modifier.FINAL | Modifier.PRIVATE);
               it.withSuperclass(threadLocalClass.describeConstable().get());
               it.withMethodBody("threadTerminated", MethodTypeDesc.of(ConstantDescs.CD_void, ConstantDescs.CD_Object),Modifier.PROTECTED, cb -> {
                   cb.aload(0);
                   cb.getfield(thisClassDesc, "callback", CallBack.class.describeConstable().get());
                   cb.loadInstruction(TypeKind.ReferenceType,   cb.parameterSlot(0));
                   cb.invokeinterface(CallBack.class.describeConstable().get(), "threadTerminated", MethodTypeDesc.of(ConstantDescs.CD_void, ConstantDescs.CD_Object));
                   cb.return_();
               });

               it.withMethodBody(ConstantDescs.INIT_NAME, MethodTypeDesc.of(ConstantDescs.CD_void, CallBack.class.describeConstable().get()), Modifier.PUBLIC, cb -> {
                   cb.aload(0);
                   cb.invokespecial(threadLocalClass.describeConstable().get(), ConstantDescs.INIT_NAME, ConstantDescs.MTD_void);
                   //this
                   cb.aload(0);
                   cb.loadInstruction(TypeKind.ReferenceType,   cb.parameterSlot(0));
                   cb.putfield(thisClassDesc, "callback", CallBack.class.describeConstable().get());
                   cb.return_();
               });
           });
           Files.write(Paths.get("proxy.class"), bytes);
           return MethodHandles.lookup().defineClass(bytes);
       }catch (Throwable throwable) {
           throw new RuntimeException(throwable);
       }
    }

    public static interface CallBack<T> {
        void threadTerminated(T value);
    }
}
