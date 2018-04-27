import java.io.File;

/**
 * @author cailei.lu
 * @description
 * @date 2018/4/27
 */

public class Application {


    public static void main(String[] args) throws Exception {

        File file = new File(Application.class.getClassLoader().getResource("hotload.properties").getFile());
        HotLoadingProperties properties = new HotLoadingProperties(file, 10000L);
        properties.openHotLoad();
        for (; ; ) {
            String s = properties.get("test");
            System.out.println(s);
            Thread.sleep(20000L);
        }


    }


}
