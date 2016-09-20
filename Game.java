import java.rmi.NotBoundException;
import java.rmi.Remote;
import java.util.Hashtable;
import java.util.Map;
import java.util.Random;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.RemoteException;

public class Game implements GameRemote {

    private static final String REFRESH = "0";
    private static final String MOVE_WEST = "1";
    private static final String MOVE_SOUTH = "2";
    private static final String MOVE_EAST = "3";
    private static final String MOVE_NORTH = "4";
    private static final String EXIT = "9";

    public static final int NORMAL = 0;
    public static final int BACKUP = 1;
    public static final int PRIMARY = 2;

    private static final String EMPTY = "";
    private static final String TREASURE = "*";

    // tracker related properties
    public String trackerIP = null;
    public String trackerPort = null;
    TrackerRemote trackerStub = null;
    
    // game play related properties
    int N = -1;
    int K = -1;
    Random rand;

    // game administration related properties
    int gameRole = NORMAL; //0 for normal, 1 for backup and 2 for primary
    String playerID = null;
    PlayerAddr myPlayerAddr;

    String primaryPlayerID = "";
    String backupPlayerID = ""  ;
    
    
    Map<String, Coord> playerCoordMap = new Hashtable<>();
    String[][] maze = new String[N][N];
    Map<String, Integer> playerScores = new Hashtable<>();
    Map<String, PlayerAddr> playerAddrMap = new Hashtable<>();

    // GUI
    GameInterface gameInterface;
    
    // Constructor
    public Game(String trackerIP, String trackerPort, String playerID) throws RemoteException, NotBoundException{
        this.trackerIP = trackerIP;
        this.trackerPort = trackerPort;
        this.playerID = playerID;
        this.rand = new Random();
        
        Registry registry = LocateRegistry.getRegistry(trackerIP);
        this.trackerStub = (TrackerRemote) registry.lookup("tracker");

        Common.registerGame(this);
        // TODO: init my player address
        // this.myPlayerAddr = new PlayerAddr()
        // any other things to init here?
    }

    private void initTreasures() {
        for(int i = 0; i < K; i++) {
            generateRandTreasure();
        }
    }


    /******   for primary server only  ******/
    // used when other player wants to join the game
    // the param and returned type for this method is not carefully considered yet
    public GameState addOtherPlayer(PlayerAddr playerAddr){
        // TODO: here the primary server should check whether it is in critical period (promoting new backup server, etc)
        // if yes just give an error and wait for the request to be retried

        // if no critical period, just add the player (happy path)
        if (isPlayersFull()) {
            return null;
        }
        addPlayerCoord(playerAddr.playerID);
        addPlayerAddr(playerAddr.playerID, playerAddr.ip_addr, playerAddr.port);
        // TODO: update to backup
        return null;
    }


    private boolean isPlayersFull() {
        return playerCoordMap.size() + K < N * N;
    }


    private void addPlayerCoord(String playerID) {
        Coord emptyCoord = getRandEmptyCoord();
        playerCoordMap.put(playerID, emptyCoord);
        maze[emptyCoord.x][emptyCoord.y] = playerID;
    }

    private void addPlayerAddr(String playerID, String ip, int port) {
        playerAddrMap.put(playerID, new PlayerAddr(ip, port, playerID));
    }

    private Coord getRandEmptyCoord() {
        Coord coord;
        do {
            coord = new Coord(rand.nextInt(N), rand.nextInt(N));
        } while (!maze[coord.x][coord.y].equals(EMPTY));
        return coord;
    }


    // called by other players to apply a move
    // @return: boolean as update result: true for success
    public GameState applyPlayerMove(String playerID, String move){
        // the actual game logic goes here
        Coord coord = playerCoordMap.get(playerID);
        int newx = coord.x, newy = coord.y;
        switch (move){
            case MOVE_WEST:
                newx --;
            case MOVE_SOUTH:
                newy ++;
            case MOVE_EAST:
                newx ++;
            case MOVE_NORTH:
                newy --;
        }
        if (!maze[newx][newy].equals(EMPTY) && !maze[newx][newy].equals(TREASURE)) {
            return prepareGameState();
        }
        if (maze[newx][newy].equals(TREASURE)) {
            generateRandTreasure();
            incrPlayerScore(playerID);
        }

        // update player coord
        playerCoordMap.put(playerID, new Coord(newx, newy));
        maze[coord.x][coord.y] = EMPTY;
        maze[newx][newy] = playerID;
        // TODO: update backup
        return prepareGameState();
    }

    private void generateRandTreasure() {
        Coord emptyCoord = getRandEmptyCoord();
        maze[emptyCoord.x][emptyCoord.y] = TREASURE;
    }

    private void incrPlayerScore(String playerID) {
        if (!playerScores.containsKey(playerID)) {
            playerScores.put(playerID, 1);
        } else {
            playerScores.put(playerID, playerScores.get(playerID) + 1);
        }
    }

    // synchronize with backup with all the game state data
    // need to consider and handle the scenario when the backup is failed

    // TODO: currently using playerID as tag, is it correct?
    private boolean updateBackup() {
        // TODO: rpc call backup.updateGameState
        PlayerAddr backupPlayerAddr = playerAddrMap.get(backupPlayerID);
        Registry registry = null;
        GameRemote remote = null;
        try {
            registry = LocateRegistry.getRegistry(backupPlayerAddr.ip_addr, backupPlayerAddr.port);
            remote = (GameRemote) registry.lookup(backupPlayerID);

            GameState gameState = prepareGameState();
            remote.updateGameState(gameState);
        }catch (Exception e) {
            // TODO: customize error handling, for this case: backup fail or something
            Common.handleError(registry, remote, backupPlayerID, e);
        }
        return false;
    }

    private GameState prepareGameState() {
        GameState gameState = new GameState();
        gameState.playerCoordMap = playerCoordMap;
        gameState.maze = maze;
        gameState.playerScores = playerScores;
        gameState.playerAddrMap = playerAddrMap;
        return gameState;
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
    public void updateGameState(GameState gameState){
        maze = gameState.maze;
        playerCoordMap = gameState.playerCoordMap;
        playerScores = gameState.playerScores;
        playerAddrMap = gameState.playerAddrMap;
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

    /******  remote method for all players  ******/

    public PlayerAddr getPrimaryServer() {
        // TODO
        return null;
    }

    /******  End of remote method for all players  ******/

    public GameRemote getPlayerStub(PlayerAddr playerAddr) throws RemoteException, NotBoundException{
        // TODO: if uncontactable, return null
        String targetPlayerIP = playerAddr.ip_addr;
        String targetPlayerID = playerAddr.playerID;
        Registry targetPlayerRegistry = LocateRegistry.getRegistry(targetPlayerID);
        GameRemote targetPlayerStub = (GameRemote) targetPlayerRegistry.lookup(targetPlayerID);

        return targetPlayerStub;
    }
;
    public boolean joinGame() throws RemoteException, NotBoundException{
        // try join game till success
        // assume the tracker never fails, it should be able to joingame just by keep retrying
        while (true) {
            // By calling tracker.GetGameInfo we should get value of N, K and optionally another playerID
            TrackerResponse response = this.trackerStub.getTrackerInfo();

            this.N = response.dim;
            this.K = response.treasures_num;            
            
            boolean joinSucceed = false;
            if (playerID == ""){
                // we will become the primary server!
                if (!this.trackerStub.addPrimaryPlayer(this.myPlayerAddr)) {
                    // fail to become the primary server...
                    // maybe another player has already become the primary
                    // ley's retry joingame
                    continue;
                }

                // initialization for primary server
                this.gameRole = 2;
                initTreasures();

            } else {
                // contact this player to get the primary server contact

                // TODO: handle uncontactable case               
                GameRemote targetPlayerStub = this.getPlayerStub(response.playerAddr);
                if (targetPlayerStub == null){
                    // player uncontactable, retry
                    continue;
                }
                
                PlayerAddr primaryServerAddr =  targetPlayerStub.getPrimaryServer();
                // TODO: handle getPrimaryServer failure here

                GameRemote primaryPlayerStub = this.getPlayerStub(primaryServerAddr);
                if (primaryPlayerStub == null){
                    // cannot contact primary server
                    continue;
                }
                
                // 2. keep calling this primary server to join game until succeed or primary server unavailable
                while (true) {
                    // call primaryPlayerID.addOtherPlayer()
                    //   if uncontactable, break this loop and continue the whole thing
                    //   if fail (primary server tells you that you join fail), just retry after some time (0.5s?)
                    //   if succeed, just break the outmost loop
                    
                    GameState gameState = primaryPlayerStub.addOtherPlayer(this.myPlayerAddr);
                    // TODO: handle uncontactable case

                    if (gameState == null) {
                        // primary is contactable but join game fail
                        // TODO: sleep and retry
                    }

                    // when succeed, the primary server should returned the most up-to-date game state 
                    // and we should update our game state accordingly
                    this.primaryPlayerID = primaryPlayerID;
                    this.updateGameState(gameState);
                    joinSucceed = true;
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

    private GameState remoteApplyMove(String nextMove) {
        PlayerAddr primaryPlayerAddr = playerAddrMap.get(primaryPlayerID);
        Registry registry = null;
        GameRemote primaryRemote = null;
        try {
            registry = LocateRegistry.getRegistry(primaryPlayerAddr.ip_addr, primaryPlayerAddr.port);
            primaryRemote = (GameRemote) registry.lookup(primaryPlayerID);
            GameState gameState = primaryRemote.applyPlayerMove(playerID, nextMove);
            return gameState;
        }catch (Exception e) {
            Common.handleError(registry, primaryRemote, primaryPlayerID, e);
        }
        return null;
    }

    public void move(String nextMove){
        switch (nextMove) {
            case REFRESH:
            case MOVE_WEST:
            case MOVE_SOUTH:
            case MOVE_EAST:
            case MOVE_NORTH:
                if (this.gameRole == PRIMARY) {
                    // I am the primary server, I can just update my gamestate
                    this.applyPlayerMove(this.playerID, nextMove);
                } else {
                    // TODO
                    // call primary server's method to update
                    // playerAddrMap.get(primaryPlayerID)
                    GameState gameState = remoteApplyMove(nextMove);
                    InterfaceData interfaceData = prepareInterfaceData(gameState);
                    gameInterface.updateInterface(interfaceData);
                    // if error = illegal move, still update the game state then ends
                    // if error is something like primary server uncontactable, then sleep and retry..
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

                    // TODO
                    // sleep for a while
                }

                // notify tracker that I have exited

                break;
            default:
                System.out.println("wrong input for game move");
        }
    }

    private InterfaceData prepareInterfaceData(GameState gameState) {
        InterfaceData interfaceData = new InterfaceData();
        interfaceData.maze = gameState.maze;
        interfaceData.playerScores = gameState.playerScores;
        return interfaceData;
    }

    public static void main(String[] args) {
        if (args.length != 3) {
            System.out.println("Wrong number of parameters...exiting");
            System.exit(0);
        }

        try {
            Game player = new Game(args[0], args[1], args[2]);
            if (player.joinGame()) {
                while (true) {
                    String nextMove = readNextMove();
                    player.move(nextMove);
                }
            }
        } catch (NotBoundException be) {
            be.printStackTrace();
        } catch (RemoteException re) {
            re.printStackTrace();
        }
    }

}

class Coord {
    public int x;
    public int y;
    public Coord(int x, int y) {
        this.x = x;
        this.y = y;
    }
}