import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import java.rmi.registry.Registry;
import java.rmi.registry.LocateRegistry;
import java.rmi.server.UnicastRemoteObject;

/*
    Tracker is the class to maintain players' IP addresses and ports,
    and acts as the entry point of all players since its IP is well-known to the public
 */
public class Tracker implements TrackerRemote {

    private static final String TAG = "getPlayerAddr";
    private Set<PlayerAddr> addr_set;
    private int dim, treasures_num;

    public Tracker(int dim, int treasures_num){
        this.addr_set = new HashSet<>();
        this.dim = dim;
        this.treasures_num = treasures_num;
    }

    public TrackerResponse getTrackerInfo(){
        TrackerResponse resp = new TrackerResponse();
        Iterator<PlayerAddr> it = addr_set.iterator();
        if ( it.hasNext() ) {
            resp.playerAddr = it.next();
        }
        resp.dim = dim;
        resp.treasures_num = treasures_num;
        return null;
    }

    public void addPlayerAddr(PlayerAddr playerAddr) {
        addr_set.add(playerAddr);
    }

    public void removePlayerAddr(PlayerAddr playerAddr) {
        addr_set.remove(playerAddr);
    }

    public static void main(String args[]) {
        TrackerRemote remote = null;
        Registry registry = null;

        try {
            // TODO: change the param to args
            Tracker obj = new Tracker(10, 10);
            remote = (TrackerRemote) UnicastRemoteObject.exportObject(obj, 0);
            registry = LocateRegistry.getRegistry();
            registry.bind(TAG, remote);
            System.out.println("remote: " + remote);
            System.err.println("Server ready");
        } catch (Exception e) {
            Common.handleError(registry, remote, TAG, e);
        }
    }

}

