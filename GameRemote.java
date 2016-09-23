import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * Created by yichao.wang on 18/9/16.
 */
public interface GameRemote extends Remote {
    // for primary server
    GameState addOtherPlayer(PlayerAddr playerAddr) throws RemoteException;
    GameState applyPlayerMove(String playerID, String move) throws RemoteException;

    // for all players
    void updateGameState(GameState gameState) throws RemoteException;
    PlayerAddr getPrimaryServer() throws RemoteException;
	void promoteSelfToBackup() throws RemoteException;
 	void ping() throws RemoteException;
}
