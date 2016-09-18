
public class Game  {
    String trackerIP = null;
    String trackerPort = null;
    String playerID = null;

    public Game(String trackerIP, String trackerPort, String playerID){
        this.trackerIP = trackerIP;
        this.trackerPort = trackerPort;
        this.playerID = playerID;

        // any other things to init here?
    }

    public boolean joinGame() {
        // retry to join game till success
        // unless there is problem with tracker

        return false;
    }

    public static String readNextMove(){
        // implement read command from standard input here
        return "0";
    }

    public static void main(String[] args) {
        if (args.length != 3) {
            System.out.println("Wrong number of parameters...exiting");
            System.exit(0);
        }

        Game player = new Game(args[0], args[1], args[2]);
        if (player.joinGame()) {
            while (true) {
                String nextMove = readNextMove();
            }
        }

    }

}