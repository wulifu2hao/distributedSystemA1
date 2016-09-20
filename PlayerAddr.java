/**
 * Created by yichao.wang on 18/9/16.
 */
public class PlayerAddr {
    // TODO: is ip_addr and port even necessary here?
    public String ip_addr;
    public int port;
    public String playerID = "";

    public PlayerAddr(String ip_addr, int port, String playerID) {
        this.ip_addr = ip_addr;
        this.port = port;
        this.playerID = playerID;
    }
}
