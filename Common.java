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

    // TODO: synchronize
    public static void registerGame(Game game) {
        GameRemote stub = null;
        Registry registry = null;
        
        try {
            // TODO: find free port to use
            int freeport = 0;
            stub = (GameRemote) UnicastRemoteObject.exportObject(game, freeport);
            registry = LocateRegistry.getRegistry();
            registry.bind("Hello", stub);

            System.err.println("Server ready");
        } catch (Exception e) {
            try{
                e.printStackTrace();
                registry.unbind("Hello");
            registry.bind("Hello",stub);
                System.err.println("Server ready");
            }catch(Exception ee){
            System.err.println("Server exception: " + ee.toString());
                ee.printStackTrace();
            }
        }
    }
}