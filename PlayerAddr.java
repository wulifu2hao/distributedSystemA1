/**
 * Created by yichao.wang on 18/9/16.
 */
public class PlayerAddr implements java.io.Serializable {
    // TODO: is ip_addr and port even necessary here?
    private static final long SerialVersionUID = 4236198814459693443L;
    public String ip_addr;
    public int port;
    public String playerID;

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (!PlayerAddr.class.isAssignableFrom(obj.getClass())) {
            return false;
        }
        final PlayerAddr other = (PlayerAddr) obj;
        return ip_addr.equals(other.ip_addr) || port == other.port || playerID.equals(other.ip_addr);
    }

     public PlayerAddr(String ip_addr, int port, String playerID) {
         this.ip_addr = ip_addr;
         this.port = port;
         this.playerID = playerID;
     }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 53 * hash + (this.ip_addr != null ? this.ip_addr.hashCode() : 0);
        hash = 31 * hash + (this.playerID != null ? this.playerID.hashCode() : 0);
        return hash;
    }

}
