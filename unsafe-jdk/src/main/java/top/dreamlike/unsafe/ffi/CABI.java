package top.dreamlike.unsafe.ffi;

public enum CABI {
    SYS_V,
    WIN_64,
    LINUX_AARCH_64,
    MAC_OS_AARCH_64,
    WIN_AARCH_64,
    AIX_PPC_64,
    LINUX_PPC_64,
    LINUX_PPC_64_LE,
    LINUX_RISCV_64,
    LINUX_S390,
    FALLBACK,
    UNSUPPORTED;
}
