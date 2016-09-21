import java.net.Inet4Address;
import java.net.UnknownHostException;
import java.rmi.Remote;
import java.rmi.registry.Registry;
import java.rmi.registry.LocateRegistry;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;

public class Common {
    public static void handleError(Registry registry, Remote remote, String tag, Exception e) {
        // e.printStackTrace();
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
            registry.bind(game.myPlayerAddr.playerID, stub);

            System.err.println("player "+game.myPlayerAddr.playerID+" ready");
        } catch (Exception e) {
            try{
                // e.printStackTrace();
                registry.unbind(game.myPlayerAddr.playerID);
                registry.bind(game.myPlayerAddr.playerID, stub);
                System.err.println("player "+game.myPlayerAddr.playerID+" ready");
            }catch(Exception ee){
                System.err.println("player exception: " + ee.toString());
                ee.printStackTrace();
            }
        }
    }

    public static InterfaceData prepareInterfaceData(GameState gameState, int role) {
        InterfaceData interfaceData = new InterfaceData();
        interfaceData.maze = gameState.maze;
        interfaceData.playerScores = gameState.playerScores;
        interfaceData.role = role;
        return interfaceData;
    }

    public static String getLocalAddress() {
        try {
            return Inet4Address.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            e.printStackTrace();
            return null;
        }
    }
}