
public class Game  {

    private static final String REFRESH = "0";
    private static final String MOVE_WEST = "1";
    private static final String MOVE_SOUTH = "2";
    private static final String MOVE_EAST = "3";
    private static final String MOVE_NORTH = "4";
    private static final String EXIT = "9";

    // tracker related properties
    String trackerIP = null;
    String trackerPort = null;
    String playerID = null;

    // game play related properties
    int N = -1;
    int K = -1;
    // player info and gameboard info is needed here

    // game administration related properties
    int gameRole = 0; //0 for normal, 1 for backup and 2 for primary
    String primaryPlayerID = "";
    String backupPlayerID = ""  ;

    public Game(String trackerIP, String trackerPort, String playerID){
        this.trackerIP = trackerIP;
        this.trackerPort = trackerPort;
        this.playerID = playerID;

        // any other things to init here?
    }

    /******   for primary server only  ******/
    // used when other player wants to join the game
    // the param and returned type for this method is not carefully considered yet
    public void addOtherPlayer(){
        // here the primary server should check whether it is in critical period (promiting new backup server, etc)
        // if yes just give and error and wait for the request to be retried
    }

    // called by other players to apply a move
    public void applyPlayerMove(){
        // the actual game logic goes here
    }

    // called by primary server itself to promote a server to backup
    // this should happens when 
    //  a) there is no backup and one player asks to join
    //  b) backup server exit
    //  c) discover backup is dead while pinging backup
    public void promoteToBackup(){

    }

    /******  End of for primary server only  ******/

    


    /******  for backup server only  ******/
    // called by primary server to update backup server state
    public void updateGameState(){

    }

    // promote self to become primary and notify other nodes
    // this can happen when 
    //  a) discover primary dead when pinging primary
    //  b) primary exit
    public void promoteSelfToPrimary(){

        // remember to kill the backup thread and start a primary thread
        // After it becomes the primary, it should call promoteToBackup to promote another server
    }





    /******  End of for backup server only  ******/

    public boolean joinGame() {
        // try join game till success
        // assume the tracker never fails, it should be able to joingame just by keep retrying
        while (true) {
            // By calling tracker.GetGameInfo we should get value of N, K and optionally another playerID

            // first we'll set this.N and this.K accordingly
            this.N = 10;
            this.K = 2+1;
            boolean joinSucceed = false;

            if (playerID == ""){
                // we will become the primary server!
                // 1. call tracker.AddPlayer(this.playerID) to add me as a player

                // 2. set my gameRole
                this.gameRole = 2;

                // 3. init a game board with players and treasure here

            } else {
                // contact this player to get the primary server contact

                // 1. call playerID's rmi method to get the primary server contact
                // if this player is uncontactable, break and retry
                String primaryPlayerID = "TODO";

                // 2. keep calling this primary server to join game until succeed or primary server unavailable
                while (true) {
                    // call primaryPlayerID.addOtherPlayer()
                    //   if uncontactable, break this loop and continue the whole thing
                    //   if fail (primary server tells you that you join fail), just retry after some time (0.5s?)
                    //   if succeed, just break the outmost loop

                    // when succeed, the primary server should returned the most up-to-date game state 
                    // and we should update our game state accordingly
                    this.primaryPlayerID = "";

                }


            }

            if (joinSucceed){
                break;
            }

        }

        // after this step, we should either
        // 1) become a primary server and 
        //  a) start a thread to periodically ping the backup server to ensure backup is live 
        //      (TODO: this may have problem since there can be only 1 player in the game)



        // 2) become a backup server and 
        //  a) start a thread to periodically ping the primary server to ensure backup is live 
        //     and prepare to become the primary server whenever primary is dead


        // 3) become normal player but know how to contact primary server to do move

        return false;
    }

    public static String readNextMove(){
        // implement read command from standard input here
        
        return "0";
    }

    public String move(String nextMove){
        switch (nextMove) {
            case REFRESH:
            case MOVE_WEST:
            case MOVE_SOUTH:
            case MOVE_EAST:
            case MOVE_NORTH:
                switch (this.gameRole){
                    case 2:
                        // I am the primary server, I can just update my gamestate 
                        // remember to update backup server also
                    break;

                    default:
                        // call primary server's method to update 
                        // if error = illegal move, still update the game state then ends
                        // if error is something like primary server uncontactable, then sleep and retry..
                    break;
                }

                break;                
            case EXIT:
                // exit
                
                // keep retrying until exit successfully
                while (true) {
                    boolean exitSucceeded = false;
                    switch (this.gameRole){
                        case 0:
                            // call the primary server to exit
                            // if uncontactable, just sleep for a while to get notified the new primary
                        break;
                        case 1:
                            // call the primary server to exit
                            // if uncontactable, it is likely that the game is undergoing critical period,
                            // just sleep and retry (next time I'm mostly likely to be the primary server)
                        break;
                        case 2:
                            // promote the backup server (if not contactable just sleep and retry)
                            // after promotion is successful, exit
                        break;
                        default:
                            System.out.println("wrong role type");
                        break;
                    }

                    if (exitSucceeded){
                        break;
                    }
                }

                // notify tracker that I have exited

                break;
            default:
                System.out.println("wrong input for game move");
        }
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
                player.move(nextMove);
            }
        }

    }

}