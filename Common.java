import java.rmi.Remote;
import java.rmi.registry.Registry;

public class Common {
    public static void handleError(Registry registry, Remote remote, String tag, Exception e) {
        System.err.println("Server exception0: " + e.toString());
        e.printStackTrace();
        try{
            registry.unbind(tag);
            registry.bind(tag, remote);
            System.err.println("Server ready");
        }catch(Exception ee){
            System.err.println("Server exception: " + ee.toString());
            ee.printStackTrace();
        }
    }
}