package top.dreamlike.hack;

import javax.management.InvalidApplicationException;
import java.lang.reflect.Field;

public class OtherMain {
    public static void main(String[] args) throws NoSuchFieldException, IllegalAccessException {

        InvalidApplicationException exception = new InvalidApplicationException("123");
        Field field = exception.getClass().getDeclaredField("val");
        field.setAccessible(true);
        Object o = field.get(exception);
    }
}
