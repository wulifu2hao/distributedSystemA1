import java.rmi.Remote;

/**
 * Created by yichao.wang on 18/9/16.
 */
public interface GameRemote extends Remote {
    void updateGameState(GameState gameState);
}
