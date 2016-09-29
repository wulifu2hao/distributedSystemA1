import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import java.rmi.registry.Registry;
import java.rmi.registry.LocateRegistry;
import java.rmi.server.UnicastRemoteObject;
import java.util.logging.Logger;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/*
    Tracker is the class to maintain players' IP addresses and ports,
    and acts as the entry point of all players since its IP is well-known to the public
 */
public class Tracker implements TrackerRemote {

    private static final String TAG = "tracker";
    private Set<PlayerAddr> addrSet;
    private Lock addrSetLock = new ReentrantLock();
    private int dim, treasures_num;
    private final Logger LOGGER = Logger.getLogger("Game");
    private String logtag = "[tracker]";

    public Tracker(int dim, int treasures_num){
        this.addrSet = new HashSet<>();
        this.dim = dim;
        this.treasures_num = treasures_num;

        String ipAddr = Common.getLocalAddress();
        if (ipAddr == null) {
            LOGGER.severe("Cannot get ip address for tracker");
            return;
        } 
        LOGGER.info(logtag + "tracker ip address: " + ipAddr);
    }

    public TrackerResponse getTrackerInfo(){
        // TODO: we may we to lock addrSet here for read also
        TrackerResponse resp = new TrackerResponse();
        Iterator<PlayerAddr> it = addrSet.iterator();
        if ( it.hasNext() ) {
            resp.playerAddr = it.next();
        }
        resp.dim = dim;
        resp.treasures_num = treasures_num;
        return resp;
    }

    public boolean addPlayerAddr(PlayerAddr playerAddr) {
        addrSetLock.lock();
        addrSet.add(playerAddr);
        addrSetLock.unlock();
        LOGGER.info("[addPlayerAddr] playerID: " + playerAddr.playerID +", then size becomes " + getSetStr());
        return true;
    }

    public void removePlayerAddr(PlayerAddr playerAddr) {
        addrSetLock.lock();
        addrSet.remove(playerAddr);
        addrSetLock.unlock();
        LOGGER.info("[removePlayerAddr] playerID: " + playerAddr.playerID +", then size becomes " + getSetStr());
    }

    // TODO: make it synchronous so that only one player will become primary
    public boolean addPrimaryPlayer(PlayerAddr playerAddr) {
        boolean success = false;

        addrSetLock.lock();
        if (addrSet.size() == 0){
            addrSet.add(playerAddr);
            success = true;
        }        
        addrSetLock.unlock();
        
        LOGGER.info("[addPrimaryPlayer] playerID: " + playerAddr.playerID +" isSucceeded: "+success+", then size becomes " + getSetStr());
        return success;
    }

    private String getSetStr() {
        String list = "";
        for ( PlayerAddr addr : addrSet) {
            list += addr.playerID + ", ";
        }
        return list;
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

