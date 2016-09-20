/**
 * Created by yichao.wang on 18/9/16.
 */

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface TrackerRemote extends Remote{
    TrackerResponse getTrackerInfo() throws RemoteException;
    void addPlayerAddr(PlayerAddr playerAddr) throws RemoteException;
    void removePlayerAddr(PlayerAddr playerAddr) throws RemoteException;

    boolean addPrimaryPlayer(PlayerAddr playerAddr) throws RemoteException;
}
