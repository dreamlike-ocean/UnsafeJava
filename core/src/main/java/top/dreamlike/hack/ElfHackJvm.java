package top.dreamlike.hack;

import net.fornwall.jelf.ElfFile;
import net.fornwall.jelf.ElfSymbol;

import java.io.File;
import java.io.IOException;

public class ElfHackJvm {

    public long main_vm_address(File libjvmPath) throws IOException {
        ElfFile file = ElfFile.from(libjvmPath);
        ElfSymbol elfSymbol = file.getELFSymbol("main_vm");
        return elfSymbol.offset;
    }
}
