import java.rmi.Remote;

/**
 * Created by yichao.wang on 18/9/16.
 */
public interface GameRemote extends Remote {
    // for primary server
    boolean addOtherPlayer(String playerID, String playerIP, int playerPort);
    boolean applyPlayerMove(String playerID, String move);

    // for backup server
    void updateGameState(GameState gameState);
}
