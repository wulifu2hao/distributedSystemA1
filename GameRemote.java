import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * Created by yichao.wang on 18/9/16.
 */
public interface GameRemote extends Remote {
    // for primary server
    GameState addOtherPlayer(PlayerAddr playerAddr) throws RemoteException;
    GameState applyPlayerMove(String playerID, String move) throws RemoteException;

    // for backup server
    void updateGameState(GameState gameState) throws RemoteException;

    // for all players
    PlayerAddr getPrimaryServer() throws RemoteException;
}
