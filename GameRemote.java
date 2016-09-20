import java.rmi.Remote;

/**
 * Created by yichao.wang on 18/9/16.
 */
public interface GameRemote extends Remote {
    // for primary server
    GameState addOtherPlayer(PlayerAddr playerAddr);
    GameState applyPlayerMove(String playerID, String move);

    // for backup server
    void updateGameState(GameState gameState);

    // for all players
    PlayerAddr getPrimaryServer();
}
