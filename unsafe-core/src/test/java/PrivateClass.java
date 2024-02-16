import java.util.UUID;

public class PrivateClass {

    private static UUID uuid = UUID.randomUUID();


    private static String privateMethod(String a) {
        return a;
    }

    public static UUID getUuid() {
        return uuid;
    }
}
