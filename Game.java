import java.net.Inet4Address;
import java.net.UnknownHostException;
import java.rmi.NotBoundException;
import java.rmi.Remote;
import java.util.Hashtable;
import java.util.Map;
import java.util.Random;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.RemoteException;
import java.util.Scanner;
import java.util.logging.Logger;

public class Game implements GameRemote {

    // game move type
    private static final String REFRESH = "0";
    private static final String MOVE_WEST = "1";
    private static final String MOVE_SOUTH = "2";
    private static final String MOVE_EAST = "3";
    private static final String MOVE_NORTH = "4";
    private static final String EXIT = "9";

    // game role type
    public static final int NORMAL = 0;
    public static final int BACKUP = 1;
    public static final int PRIMARY = 2;

    // game board content
    private static final String EMPTY = "";
    private static final String TREASURE = "*";

    private static final int SLEEP_PERIOD = 100;

    // tracker related properties
    public String trackerIP = null;
    public String trackerPort = null;
    TrackerRemote trackerStub = null;
    

    // game play related properties
    int N = -1;
    int K = -1;

    // game administration related properties
    int gameRole = NORMAL; //0 for normal, 1 for backup and 2 for primary
    String primaryPlayerID = "";
    String backupPlayerID = ""  ;
    PlayerAddr myPlayerAddr;
    
    // game state
    Map<String, Coord> playerCoordMap = new Hashtable<>();
    String[][] maze;
    Map<String, Integer> playerScores = new Hashtable<>();
    Map<String, PlayerAddr> playerAddrMap = new Hashtable<>();

    private static final int DEFAULT_PORT = 0;

    // GUI
    GameInterface gameInterface;

    // logging
    private final Logger LOGGER = Logger.getLogger("Game");

    private Random rand;

    // Constructor
    public Game(String trackerIP, String trackerPort, String playerID) throws RemoteException, NotBoundException{
        this.trackerIP = trackerIP;
        this.trackerPort = trackerPort;
        this.rand = new Random();
        
        Registry registry = LocateRegistry.getRegistry(trackerIP);
        this.trackerStub = (TrackerRemote) registry.lookup("tracker");

        String ipAddr = Common.getLocalAddress();
        if (ipAddr == null) {
            LOGGER.severe("Cannot get ip address for " + playerID);
            return;
        }
        // this.myPlayerAddr = new PlayerAddr(ipAddr, DEFAULT_PORT, playerID);
        this.myPlayerAddr = new PlayerAddr();
        this.myPlayerAddr.playerID = playerID;
        // TODO complete the fields
        Common.registerGame(this);
        // any other things to init here?
    }

    private void initTreasures() {
        for(int i = 0; i < K; i++) {
            generateRandTreasure();
        }
    }

    private void initGameState(){
        maze = new String[this.N][this.N];
        for (int i=0; i<N; i++){
            for (int j=0; j<N; j++){
                maze[i][j] = EMPTY;
            }
        }
        initTreasures();

        playerScores.put(myPlayerAddr.playerID, 0);
        playerAddrMap.put(myPlayerAddr.playerID, myPlayerAddr);

        maze[1][1] = myPlayerAddr.playerID;
        playerCoordMap.put(myPlayerAddr.playerID, new Coord(1, 1));
    }


    /******   for primary server only  ******/
    // used when other player wants to join the game
    // the param and returned type for this method is not carefully considered yet
    public GameState addOtherPlayer(PlayerAddr playerAddr){
        // TODO: here the primary server should check whether it is in critical period (promoting new backup server, etc)
        // if yes just give an error and wait for the request to be retried

        // if no critical period, just add the player (happy path)
        if (isPlayersFull()) {
            LOGGER.info("player is full");
            return null;
        }

        addPlayerCoord(playerAddr.playerID);
        addPlayerAddr(playerAddr.playerID, playerAddr.ip_addr, playerAddr.port);

        LOGGER.info("finish adding player "+ playerAddr.playerID+ " to gamestate");

        // TODO: if no backup then use it as backup

        // TODO: update to backup
        return prepareGameState();
    }


    private boolean isPlayersFull() {
        return playerCoordMap.size() + K >= N * N;
    }


    private void addPlayerCoord(String playerID) {
        Coord emptyCoord = getRandEmptyCoord();
        playerCoordMap.put(playerID, emptyCoord);
        maze[emptyCoord.x][emptyCoord.y] = playerID;
    }

    private void addPlayerAddr(String playerID, String ip, int port) {
        // playerAddrMap.put(playerID, new PlayerAddr(ip, port, playerID));
        PlayerAddr newPlayer = new PlayerAddr();
        newPlayer.playerID = playerID;
        // TODO
        playerAddrMap.put(playerID, newPlayer);
    }

    private Coord getRandEmptyCoord() {
        Coord coord;
        do {
            coord = new Coord(rand.nextInt(N), rand.nextInt(N));
        } while (!maze[coord.x][coord.y].equals(EMPTY));
        return coord;
    }


    private GameState applyPlayerExit(String playerID) {
        Coord coord = playerCoordMap.get(playerID);
        maze[coord.x][coord.y] = EMPTY;
        playerAddrMap.remove(playerID);
        playerCoordMap.remove(playerID);
        playerScores.remove(playerID);
        boolean ok = updateBackup();
        if (!ok){
            //TODO: thread backup to recover it
            return null;
        }
        return prepareGameState();
    }


    // called by other players to apply a move
    // @return: boolean as update result: true for success
    public GameState applyPlayerMove(String playerID, String move){
        // the actual game logic goes here
        Coord coord = playerCoordMap.get(playerID);
        int newx = coord.x, newy = coord.y;
        switch (move){
            case REFRESH:
                return prepareGameState();
            case EXIT:
                return applyPlayerExit(playerID);
            case MOVE_WEST:
                newx --; break;
            case MOVE_SOUTH:
                newy ++; break;
            case MOVE_EAST:
                newx ++; break;
            case MOVE_NORTH:
                newy --; break;
        }
        if (newx < 0 || newx >= N || newy < 0 || newy >= N) {
            LOGGER.warning("Illegal move");
            return prepareGameState();
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
        LOGGER.fine("generate treasure at " + emptyCoord.x + " : " + emptyCoord.y);
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
            return true;
        }catch (Exception e) {
            // TODO: customize error handling, for this case: backup fail or something
            Common.handleError(registry, remote, backupPlayerID, e);
            return false;
        }
        
    }

    private GameState prepareGameState() {
        GameState gameState = new GameState();
        gameState.playerCoordMap = playerCoordMap;
        gameState.maze = maze;
        gameState.playerScores = playerScores;
        gameState.playerAddrMap = playerAddrMap;
        return gameState;
    }

    // called by primary server itself to promote another server to backup
    // this should happens when 
    //  a) there is no backup and one player asks to join
    //  b) backup server exit
    //  c) discover backup is dead while pinging backup
    public void promoteSomeoneToBackup(){

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
        // 0. set critical flag

        // 1. update setting to make self primary

        // 2. notify other players
        // note: 
        //  a) if the original primary crashes, 
        //     then we just found that we fail to update it, it should be ok
        //  b) if he asks for exit, and has not really exited yet, will it be a problem?

        // 2.5 from the result of notifying other players
        //     we know who are dead and should remove them from tracker
        //     this should at least help us removing the crashed primary server
        
        // 3. promote another to be backup if possible

        // 4. start the primaryHelper thread 

        // 5. clear critical flag
    }

    /******  End of for backup server only  ******/

    /******  remote method for all players  ******/

    public PlayerAddr getPrimaryServer() {
        LOGGER.info("getPrimaryServer call");
        PlayerAddr result = playerAddrMap.get(primaryPlayerID);
        LOGGER.info("primary server result: " + result.playerID);
        return result;
    }

    public void ping(){
        return ;
    }

    // this remote method is called by the actual primary server
    // it assumes the player has the correct knowledge of the primary server
    public void promoteSelfToBackup(){
        // 1. update setting to make self primary

        // 2. start the backupHelper thread 
        (new Thread(new BackupHelper(this))).start();
    }



    /******  End of remote method for all players  ******/

    GameRemote getPlayerStub(PlayerAddr playerAddr) throws RemoteException, NotBoundException{
        String targetPlayerIP = playerAddr.ip_addr;
        String targetPlayerID = playerAddr.playerID;
        Registry targetPlayerRegistry = LocateRegistry.getRegistry(targetPlayerIP);
        GameRemote targetPlayerStub = (GameRemote) targetPlayerRegistry.lookup(targetPlayerID);
        return targetPlayerStub;
    }


    public boolean joinGame() throws RemoteException, NotBoundException, InterruptedException{
        LOGGER.info("trying to join game");
        // try join game till success
        // assume the tracker never fails, it should be able to joingame just by keep retrying
        while (true) {
            // By calling tracker.GetGameInfo we should get value of N, K and optionally another playerID
            TrackerResponse response = this.trackerStub.getTrackerInfo();            

            this.N = response.dim;
            this.K = response.treasures_num;            
            
            boolean joinSucceed = false;
            if (response.playerAddr == null){
                LOGGER.info("trying to join as primary server");
                // we will become the primary server!
                if (!this.trackerStub.addPrimaryPlayer(this.myPlayerAddr)) {
                    // fail to become the primary server
                    // maybe another player has already become the primary
                    // ley's retry joingame
                    LOGGER.info("fail to join as primary server");
                    continue;
                }

                // initialization for primary server
                this.gameRole = 2;
                this.primaryPlayerID = myPlayerAddr.playerID;
                initGameState();                
                gameInterface = GameInterface.initGameInterface(myPlayerAddr.playerID, Common.prepareInterfaceData(prepareGameState()));
                joinSucceed = true;
                LOGGER.info("join game succeeded");

            } else {
                // contact this player to get the primary server contact
                LOGGER.info("contacting player " + response.playerAddr.playerID + " to get primary");

                boolean isUncontactable = false;
                PlayerAddr primaryServerAddr = null;
                try {
                    GameRemote targetPlayerStub = this.getPlayerStub(response.playerAddr);                
                    if (targetPlayerStub == null){
                        // LOGGER.warning("get primary player stub fail when joining game");
                        isUncontactable = true;
                    } else {
                        primaryServerAddr =  targetPlayerStub.getPrimaryServer();    
                    }                    
                } catch (Exception e) {
                    // TODO: log this exception in a simple way
                    e.printStackTrace();
                    LOGGER.warning("another player with id: "+response.playerAddr.playerID+" uncontactable! " + e);
                    isUncontactable = true;
                }             

                if (isUncontactable || primaryServerAddr == null) {
                    // retry
                    // LOGGER.info("fail to get primaryServerAddr, retry...");
                    continue;
                }
                
                LOGGER.info("successfully obtain primary server contact!");
                // 2. keep calling this primary server to join game until succeed or primary server unavailable
                while (true) {
                    // try to ask primary to add me to the game
                    isUncontactable = false;
                    GameState gameState = null;
                    try{
                        GameRemote primaryPlayerStub = this.getPlayerStub(primaryServerAddr);
                        if (primaryPlayerStub == null) {
                            isUncontactable = true;
                        } else {
                            gameState = primaryPlayerStub.addOtherPlayer(this.myPlayerAddr);    
                        }
                    } catch (Exception e) {
                        LOGGER.warning("primary server with id: "+ primaryServerAddr.playerID+" uncontactable when joining game");
                        isUncontactable = true;
                    }

                    if (isUncontactable) {
                        // fail because primary not contactable
                        // break this loop and continue the whole thing
                        break;
                    }

                    if (gameState == null) {
                        // fail because primary doesn't allow you to join
                        // TODO: sleep for some time here
                        LOGGER.info("primary server doesn't allow join game");
                        Thread.sleep(SLEEP_PERIOD);
                        continue;
                    }

                    LOGGER.info("successfully allowed to join game by primary server");
                    // when succeed, the primary server should returned the most up-to-date game state 
                    // and we should update our game state accordingly
                    this.primaryPlayerID = primaryServerAddr.playerID;
                    this.updateGameState(gameState);
                    gameInterface = GameInterface.initGameInterface(myPlayerAddr.playerID, Common.prepareInterfaceData(prepareGameState()));
                    joinSucceed = true;
                    LOGGER.info("join game succeeded");
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

        return true;
    }



    private GameState remoteApplyMove(String nextMove) {
        PlayerAddr primaryPlayerAddr = playerAddrMap.get(primaryPlayerID);
        Registry registry = null;
        GameRemote primaryRemote = null;
        try {
            registry = LocateRegistry.getRegistry(primaryPlayerAddr.ip_addr, primaryPlayerAddr.port);
            primaryRemote = (GameRemote) registry.lookup(primaryPlayerID);
            GameState gameState = primaryRemote.applyPlayerMove(this.myPlayerAddr.playerID, nextMove);
            return gameState;
        }catch (Exception e) {
            LOGGER.warning("[remoteApplyMove] error: " + e);
            Common.handleError(registry, primaryRemote, primaryPlayerID, e);
            // connection error return null
            return null;
        }
    }

    public void move(String nextMove) throws InterruptedException{
        switch (nextMove) {
            case REFRESH:
            case MOVE_WEST:
            case MOVE_SOUTH:
            case MOVE_EAST:
            case MOVE_NORTH:
                if (this.gameRole == PRIMARY) {
                    // I am the primary server, I can just update my gamestate
                    GameState gameState = this.applyPlayerMove(this.myPlayerAddr.playerID, nextMove);
                    gameInterface.updateInterface(Common.prepareInterfaceData(gameState));
                } else {
                    // TODO
                    // call primary server's method to update
                    // playerAddrMap.get(primaryPlayerID)
                    // if error = illegal move, still update the game state then ends
                    GameState gameState = remoteApplyMove(nextMove);

                    // if error is something like primary server uncontactable, then sleep and retry..
                    while (gameState == null) {
                        Thread.sleep(SLEEP_PERIOD);
                        gameState = remoteApplyMove(nextMove);
                        LOGGER.warning("[move] remoteApplyMove gameState = NULL, retry");
                    }
                    InterfaceData interfaceData = Common.prepareInterfaceData(gameState);
                    gameInterface.updateInterface(interfaceData);
                }

                break;                
            case EXIT:
                // exit
                
                // keep retrying until exit successfully
                while (true) {
                    boolean exitSucceeded = false;
                    switch (this.gameRole){
                        case NORMAL:
                            // call the primary server to exit
                            // if uncontactable, just sleep for a while to get notified the new primary
                            while (remoteApplyMove(EXIT) == null){
                                Thread.sleep(SLEEP_PERIOD);
                                LOGGER.warning("[move] remoteApplyExit fail, retry");
                            }
                        break;
                        case BACKUP:
                            // call the primary server to exit
                            // if uncontactable, it is likely that the game is undergoing critical period,
                            // just sleep and retry (next time I'm mostly likely to be the primary server)
                            while (remoteApplyMove(EXIT) == null){
                                Thread.sleep(SLEEP_PERIOD);
                                LOGGER.warning("[move] remoteApplyExit fail, retry");
                            }
                        break;
                        case PRIMARY:
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

    public static void main(String[] args) {
        if (args.length != 3) {
            System.out.println("Wrong number of parameters...exiting");
            System.exit(0);
        }
        
        Scanner keyboard = new Scanner(System.in);

        try {
            Game player = new Game(args[0], args[1], args[2]);
            // keep retrying joinGame
            if (!player.joinGame()){
                System.out.println("[main] join game fail, exit");
                System.exit(0);
            }

            while (true) {
                String nextMove = keyboard.nextLine();
                player.move(nextMove);
                if (nextMove == EXIT ){
                    break;
                }
            }
        } catch (NotBoundException be) {
            be.printStackTrace();
        } catch (RemoteException re) {
            re.printStackTrace();
        } catch (InterruptedException ie) {
            System.out.println(ie);
            Thread.currentThread().interrupt();
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