package top.dreamlike.unsafe;

import java.lang.foreign.Arena;
import java.util.HashMap;
import java.util.Scanner;

public class main {
    public static void main(String[] args) {
        System.out.println("wait");
        new Scanner(System.in).next();
        try(Arena arena = Arena.ofConfined()) {
            JNIEnv jniEnv = new JNIEnv(arena);
            var aShort = jniEnv.GetStaticFieldByName(JNIEnv.class.getDeclaredField("JNI_VERSION")).getInt();
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }
}
