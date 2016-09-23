import java.io.Serializable;
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
        this.myPlayerAddr = new PlayerAddr(ipAddr, DEFAULT_PORT, playerID);
        this.myPlayerAddr.playerID = playerID;
        Common.registerGame(this);
        // TODO: any other things to init here?
    }



    /******   for primary server only  ******/
    // used when other player wants to join the game
    // the param and returned type for this method is not carefully considered yet
    public GameState addOtherPlayer(PlayerAddr playerAddr){
        // TODO: here the primary server should check whether it is in critical period (promoting new backup server, etc)
        // if yes just give an error and wait for the request to be retried

        // if no critical period, just add the player (happy path)
        if (isPlayersFull()) {
            LOGGER.info("[addOtherPlayer] player is full");
            return null;
        }

        addPlayerScore(playerAddr.playerID);
        addPlayerCoord(playerAddr.playerID);
        addPlayerAddr(playerAddr);


        LOGGER.info("[addOtherPlayer] finish adding player "+ playerAddr.playerID+ " to gamestate");

        GameState gameState = prepareGameState();
        LOGGER.severe("[addOtherPlayer] after adding playerAddr size: " + playerAddrMap.size());

        // TODO: if no backup then use it as backup
        if (this.playerAddrMap.size() <= 1) {
            LOGGER.severe("[addOtherPlayer] invalid playerAddrMap size");
        } else if (this.playerAddrMap.size() == 2) {
            // TODO: this is one of the place that "promote a player to backup"
            //       but the difference is that the player has not finished joining the game.
            //       shall we distinguish this case with other promoting to backup cases?
            backupPlayerID = playerAddr.playerID;
            gameState.isBecomeBackup = true;
        } else {
            boolean ok = updateBackup();
            if (!ok) {
                LOGGER.warning("update backup fail");
                // TODO: recover backup
            }
        }

        udpateGameInterface();
        return gameState;
    }

    // called by other players to apply a move
    // @return: GameState as update result
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
        boolean ok = updateBackup();
        if (!ok) {
            LOGGER.warning("update backup fail");
            // TODO: recover backup
        }
        udpateGameInterface();
        return prepareGameState();
    }



    // synchronize with backup with all the game state data
    // need to consider and handle the scenario when the backup is failed

    // TODO: currently using playerID as tag, is it correct?
    private boolean updateBackup() {
        // TODO: rpc call backup.updateGameState
        LOGGER.info("[updateBackup] playersize: " + playerAddrMap.size());
        PlayerAddr backupPlayerAddr = playerAddrMap.get(backupPlayerID);
        Registry registry = null;
        GameRemote remote = null;
        try {
            registry = LocateRegistry.getRegistry(backupPlayerAddr.ip_addr, backupPlayerAddr.port);
            if (registry == null ){
                return false;
            }
            remote = (GameRemote) registry.lookup(backupPlayerID);

            GameState gameState = prepareGameState();
            remote.updateGameState(gameState);
            return true;
        }catch (Exception e) {
            // TODO: customize error handling, for this case: backup fail or something
            // TODO: I think using handleError is problematic
            //       because if backup has failed, we should not rebind its tag!
            Common.handleError(registry, remote, backupPlayerID, e);
            LOGGER.warning("update backup fail. Error: "+e);
            return false;
        }
        
    }



    // called by primary server itself to promote another server to backup
    // Currently it is called when
    //   primary helper discover backup is dead while pinging backup
    // TODO:
    //  there are 2 other cases where we should promote another server to backup
    //  a) there is no backup and one player asks to join
    //     this case is different since the player just joined 
    //     and we are sure that he is going to the backup (so we don't need the "findSomeone" part)
    //  b) backup server exit
    //     this case needs to "promoteSomeoneToBackup". 
    //     Shall we just let it go and let primary helper to detect it?
    public void promoteSomeoneToBackup(){
        // TODO: remember to set critical flag here

        // since we want to promote someone to backup, 
        // there must be no current backup OR current backup fails
        // let's set current backup to empty anyway
        this.backupPlayerID = "";

        // find somebody to promote to backup
        for (Map.Entry<String, PlayerAddr> entry : playerAddrMap.entrySet()) {
            String playerID = entry.getKey();
            PlayerAddr playerAddr = entry.getValue();

            if (playerID == this.myPlayerAddr.playerID) {
                continue;
            }

            boolean promoteSucceeded = false;
            try {               
                if (playerAddr == null){
                    LOGGER.warning("Impossible! we got null value by iterating a map! playerID: "+playerID);
                } else {
                    GameRemote playerStub = getPlayerStub(playerAddr);                
                    if (playerStub != null){                        
                        playerStub.promoteSelfToBackup();
                        // if we can reach here without throwing exception, 
                        // the promotion is successful!
                        promoteSucceeded = true;
                    }      
                }                               
            } catch (Exception e) {
                // do nothing
            } 

            if (promoteSucceeded){
                this.backupPlayerID = playerID;
                break;
            } else {
                // TODO: shall we remove a dead player here or
                //       shall we leave it to the primaryHelper?
            }
        }
    }

    // this method is used by the primary server (not remote call)
    // to remove a player from both game state and tracker
    // TODO: implement this method
    public void forceRemovePlayer(String playerID){
        // this method should be able to make use of method applyPlayerExit
    }

    /******  End of for primary server only  ******/

    


    /******  for backup server only  ******/
    // called by primary server to update backup server state
    // TODO: now it becomes a bit confusing. should this be a method for any normal player?
    public void updateGameState(GameState gameState){
        LOGGER.info("[updateGameState] update player size: " + gameState.playerAddrMap.size());
        maze = gameState.maze;
        playerCoordMap = gameState.playerCoordMap;
        playerScores = gameState.playerScores;
        playerAddrMap = gameState.playerAddrMap;
        if (gameState.isBecomeBackup) {
            // TODO: promoteSelfToBackup is designed to be a remote method
            // here it is called locally, will it be a problem?
            promoteSelfToBackup();
        }
        udpateGameInterface();
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
        (new Thread(new PrimaryHelper(this))).start();

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
    // TODO: now it looks like this method should always succeed 
    //  and thus no need return any value, is it true?
    public void promoteSelfToBackup(){        
        // 1. update setting to make self backup
        gameRole = BACKUP;
        backupPlayerID = myPlayerAddr.playerID;

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
                this.gameRole = PRIMARY;
                this.primaryPlayerID = myPlayerAddr.playerID;
                initGameState();                
                joinSucceed = true;
                LOGGER.info("join game succeeded");

            } else {

                PlayerAddr primaryServerAddr = contactPlayer(response.playerAddr);
                if (primaryServerAddr == null){
                    continue;
                }

                LOGGER.info("successfully obtain primary server contact!");
                // 2. keep calling this primary server to join game until succeed or primary server unavailable
                joinSucceed = tryJoinPrimary(primaryServerAddr);
            }

            if (joinSucceed){
                gameInterface = GameInterface.initGameInterface(myPlayerAddr.playerID, Common.prepareInterfaceData(prepareGameState(), gameRole));
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

    private PlayerAddr contactPlayer(PlayerAddr playerAddr) throws RemoteException {
        // contact this player to get the primary server contact
        LOGGER.info("contacting player " + playerAddr.playerID + " to get primary");

        boolean isUncontactable = false;
        PlayerAddr primaryServerAddr = null;
        try {
            GameRemote targetPlayerStub = this.getPlayerStub(playerAddr);
            if (targetPlayerStub == null){
                // LOGGER.warning("get primary player stub fail when joining game");
                isUncontactable = true;
            } else {
                primaryServerAddr =  targetPlayerStub.getPrimaryServer();
            }
        } catch (Exception e) {
            // TODO: log this exception in a simple way
//            e.printStackTrace();
            LOGGER.warning("another player with id: "+ playerAddr.playerID+" uncontactable! " + e);
            isUncontactable = true;
        }

        if (isUncontactable) {
            LOGGER.info("fail to contact the contact player, retry...");
            trackerStub.removePlayerAddr(playerAddr);
            return null;
        }
        if (primaryServerAddr == null) {
            LOGGER.info("get primaryServerAddr null, retry...");
            return null;
        }
        return primaryServerAddr;
    }

    private boolean tryJoinPrimary(PlayerAddr primaryServerAddr) throws InterruptedException{
        // 2. keep calling this primary server to join game until succeed or primary server unavailable
        while (true) {
            // try to ask primary to add me to the game
            boolean isUncontactable = false;
            GameState gameState = null;
            try{
                GameRemote primaryPlayerStub = this.getPlayerStub(primaryServerAddr);
                if (primaryPlayerStub == null) {
                    isUncontactable = true;
                } else {
                    gameState = primaryPlayerStub.addOtherPlayer(this.myPlayerAddr);
                }
            } catch (Exception e) {
                LOGGER.warning("[tryJoinPrimary] primary server with id: "+ primaryServerAddr.playerID+" not contactable. Error: " + e);
                isUncontactable = true;
            }

            if (isUncontactable) {
                // fail because primary not contactable
                // break this loop and continue the whole thing
                return false;
            }

            if (gameState == null) {
                // fail because primary doesn't allow you to join -> sleep and then continue
                LOGGER.info("primary server doesn't allow join game");
                Thread.sleep(SLEEP_PERIOD);
                continue;
            }

            LOGGER.info("successfully allowed to join game by primary server");
            // update game state
            this.primaryPlayerID = primaryServerAddr.playerID;
            this.updateGameState(gameState);
            LOGGER.info("join game succeeded");
            return true;
        }
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
                    gameInterface.updateInterface(Common.prepareInterfaceData(gameState, gameRole));
                } else {
                    GameState gameState = remoteApplyMove(nextMove);

                    // if error is something like primary server uncontactable, then sleep and retry..
                    while (gameState == null) {
                        Thread.sleep(SLEEP_PERIOD);
                        gameState = remoteApplyMove(nextMove);
                        LOGGER.warning("[move] remoteApplyMove gameState = NULL, retry");
                    }
                    InterfaceData interfaceData = Common.prepareInterfaceData(gameState, gameRole);
                    gameInterface.updateInterface(interfaceData);
                }

                break;                
            case EXIT:
                // exit
                
                // keep retrying until exit successfully
                // TODO: currently the sleeping and retrying are done inside the switch role block
                //       but as time passes our role may change. 
                //       is it better just to let the outer do the sleep and retry?
                // TODO: shall we let the tracker know we have exited?
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
                            // TODO: shall we set exitSucceeded to be true so that it can break the loop?
                        break;
                        case BACKUP:
                            // call the primary server to exit
                            // if uncontactable, it is likely that the game is undergoing critical period,
                            // just sleep and retry (next time I'm mostly likely to be the primary server)
                            while (remoteApplyMove(EXIT) == null){
                                Thread.sleep(SLEEP_PERIOD);
                                LOGGER.warning("[move] remoteApplyExit fail, retry");
                            }
                            // TODO: shall we set exitSucceeded to be true so that it can break the loop?
                        break;
                        case PRIMARY:
                            // TODO: 
                            // if there is backup, it should be pinging the primary
                            //   so if we just exit, there should not be a problem right?
                            // if there is no backup, we can also just exit
                            // 
                        break;
                        default:
                            System.out.println("wrong role type");
                        break;
                    }

                    if (exitSucceeded){
                        break;
                    }

                    // TODO
                    // sleep and retry
                }

                break;
            default:
                System.out.println("wrong input for game move");
        }
    }



    /********** init ************/
    private void initTreasures() {
        for(int i = 0; i < K; i++) {
            generateRandTreasure();
        }
    }

    private void initMaze() {
        maze = new String[this.N][this.N];
        for (int i=0; i<N; i++){
            for (int j=0; j<N; j++){
                maze[i][j] = EMPTY;
            }
        }
    }

    private void initPlayerData() {
        Coord emptyCoord = getRandEmptyCoord();
        maze[emptyCoord.x][emptyCoord.y] = myPlayerAddr.playerID;
        playerCoordMap.put(myPlayerAddr.playerID, emptyCoord);
        playerScores.put(myPlayerAddr.playerID, 0);
        playerAddrMap.put(myPlayerAddr.playerID, myPlayerAddr);
    }

    private void initGameState(){
        initMaze();
        initTreasures();
        initPlayerData();
    }

    /******* auxiliary *******/

    private void udpateGameInterface() {
        if (gameInterface != null ) {
            gameInterface.updateInterface(Common.prepareInterfaceData(prepareGameState(), gameRole));
        }
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

    private void addPlayerScore(String playerID) {
        playerScores.put(playerID, 0);
    }

    private void addPlayerCoord(String playerID) {
        Coord emptyCoord = getRandEmptyCoord();
        playerCoordMap.put(playerID, emptyCoord);
        maze[emptyCoord.x][emptyCoord.y] = playerID;
    }

    private void addPlayerAddr(PlayerAddr playerAddr) {
        playerAddrMap.put(playerAddr.playerID, playerAddr);
    }

    private GameState prepareGameState() {
        GameState gameState = new GameState();
        gameState.playerCoordMap = playerCoordMap;
        gameState.maze = maze;
        gameState.playerScores = playerScores;
        gameState.playerAddrMap = playerAddrMap;
        return gameState;
    }

    private Coord getRandEmptyCoord() {
        Coord coord;
        do {
            coord = new Coord(rand.nextInt(N), rand.nextInt(N));
        } while (!maze[coord.x][coord.y].equals(EMPTY));
        return coord;
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

    private boolean isPlayersFull() {
        return playerCoordMap.size() + K >= N * N;
    }


}

class Coord implements Serializable{
    public int x;
    public int y;
    public Coord(int x, int y) {
        this.x = x;
        this.y = y;
    }
}