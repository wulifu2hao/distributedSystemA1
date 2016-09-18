import java.util.Hashtable;
import java.util.Map;

/**
 * Created by yichao.wang on 18/9/16.
 */
public class GameState {
    public Map<String, Coord> coord_map;
    public String[][] player_maze;
    public boolean[][] treasure_maze;
    public Map<String, PlayerAddr> playerAddrMap;
}
