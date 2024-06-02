package top.dreamlike.unsafe.stackWalker;

import top.dreamlike.unsafe.core.MasterKey;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Set;

public class LiveStackWalker {

    private static final MethodHandle STACK_WALKER_MH = initStackWalkerMH();

    private static MethodHandle initStackWalkerMH() {
        try {
            Class<?> exClass = Class.forName("java.lang.StackWalker$ExtendedOption");
            Object constant = exClass.getEnumConstants()[0]; // 就一个 直接取第一个
            MethodHandle newInstanceMH =  MasterKey.INSTANCE.getTrustedLookup()
                    .findStatic(StackWalker.class, "newInstance", MethodType.methodType(StackWalker.class, Set.class, exClass))
                    .asType(MethodType.methodType(StackWalker.class, Set.class, Object.class));
            newInstanceMH = MethodHandles.insertArguments(newInstanceMH, 1, constant);
            return newInstanceMH;
        }catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    public static StackWalker stackWalker(Set<StackWalker.Option> options) {
        try {
            return (StackWalker) STACK_WALKER_MH.invokeExact(options);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

}
