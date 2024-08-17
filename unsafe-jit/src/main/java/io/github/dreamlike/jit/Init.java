package io.github.dreamlike.jit;

import top.dreamlike.unsafe.core.MasterKey;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Optional;

class Init {

    static {
        try {
            exportsJVMCI();
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }
    private static void exportsJVMCI() throws Throwable {
        MethodHandles.Lookup trustedLookup = MasterKey.INSTANCE.getTrustedLookup();
        MethodHandle methodHandle = trustedLookup.findVirtual(Module.class, "implAddOpensToAllUnnamed", MethodType.methodType(void.class, String.class));
       // -XX:+UnlockExperimentalVMOptions -XX:+EnableJVMCI
        Optional<Module> jvmciModuleOptional = ModuleLayer.boot().findModule("jdk.internal.vm.ci");
        if (jvmciModuleOptional.isEmpty()) {
            throw new IllegalStateException("JVMCI module not found. Use -XX:+UnlockExperimentalVMOptions -XX:+EnableJVMCI");
        }
        Module jvmciModule = jvmciModuleOptional.get();
        methodHandle.invoke(jvmciModule, "jdk.vm.ci.code");
        methodHandle.invoke(jvmciModule, "jdk.vm.ci.code.site");
        methodHandle.invoke(jvmciModule, "jdk.vm.ci.hotspot");
        methodHandle.invoke(jvmciModule, "jdk.vm.ci.meta");
        methodHandle.invoke(jvmciModule, "jdk.vm.ci.runtime");
    }
}
