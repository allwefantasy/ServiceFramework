/**
 * 3/29/14 WilliamZhu(allwefantasy@gmail.com)
 */
public class Test {
    public static void main(String[] args) {
        new Test().a();
    }

    public void a() {
        StackTraceElement ste = Thread.currentThread().getStackTrace()[1];
        System.out.println(ste.getClassName() + "." + ste.getMethodName());
        b();
    }

    public void b() {
        System.out.println("who am i");
    }
}
