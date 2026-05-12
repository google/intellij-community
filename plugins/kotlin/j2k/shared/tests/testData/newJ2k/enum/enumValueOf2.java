// IGNORE_K1
public class J {
    public static <T extends Enum<T>> void crash(Class<T> clazz) {
        Enum.valueOf(clazz, "X");
    }
}
