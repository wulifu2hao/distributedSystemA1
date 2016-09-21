import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import java.rmi.registry.Registry;
import java.rmi.registry.LocateRegistry;
import java.rmi.server.UnicastRemoteObject;
import java.util.logging.Logger;

/*
    Tracker is the class to maintain players' IP addresses and ports,
    and acts as the entry point of all players since its IP is well-known to the public
 */
public class Tracker implements TrackerRemote {

    private static final String TAG = "tracker";
    private Set<PlayerAddr> addrSet;
    private int dim, treasures_num;
    private final Logger LOGGER = Logger.getLogger("Game");

    public Tracker(int dim, int treasures_num){
        this.addrSet = new HashSet<>();
        this.dim = dim;
        this.treasures_num = treasures_num;
    }

    public TrackerResponse getTrackerInfo(){
        TrackerResponse resp = new TrackerResponse();
        Iterator<PlayerAddr> it = addrSet.iterator();
        if ( it.hasNext() ) {
            resp.playerAddr = it.next();
        }
        resp.dim = dim;
        resp.treasures_num = treasures_num;
        return resp;
    }

    public void addPlayerAddr(PlayerAddr playerAddr) {
        addrSet.add(playerAddr);
        LOGGER.info("[addPlayerAddr] playerID: " + playerAddr.playerID +", then size becomes " + addrSet.size());
    }

    public void removePlayerAddr(PlayerAddr playerAddr) {
        addrSet.remove(playerAddr);
        LOGGER.info("[removePlayerAddr] playerID: " + playerAddr.playerID +", then size becomes " + addrSet.size());
    }

    // TODO: make it synchronous so that only one player will become primary
    public boolean addPrimaryPlayer(PlayerAddr playerAddr) {
        addrSet.add(playerAddr);
        System.out.println("add primary");
        return true;
    }


    public static void main(String args[]) {
        if (args.length != 3) {
            System.out.println("Wrong number of parameters...exiting");
            System.exit(0);
        }

        int port = Integer.parseInt(args[0]);
        int N = Integer.parseInt(args[1]);
        int K = Integer.parseInt(args[2]);

        TrackerRemote remote = null;
        Registry registry = null;

        try {
            // TODO: change the param to args
            Tracker obj = new Tracker(N, K);
            remote = (TrackerRemote) UnicastRemoteObject.exportObject(obj, port);
            registry = LocateRegistry.getRegistry();
            registry.bind(TAG, remote);
            System.out.println("remote: " + remote);
            System.out.println("Server ready");
        } catch (Exception e) {
            Common.handleError(registry, remote, TAG, e);
        }
    }

}

