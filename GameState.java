import java.util.Hashtable;
import java.util.Map;

/**
 * Created by yichao.wang on 18/9/16.
 */
public class GameState implements java.io.Serializable {
    public Map<String, Coord> playerCoordMap;
    public String[][] maze;
    public Map<String, Integer> playerScores;
    public Map<String, PlayerAddr> playerAddrMap;
}
