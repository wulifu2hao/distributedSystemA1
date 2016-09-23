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
        Registry registry = LocateRegistry.getRegistry(trackerIP);
        this.trackerStub = (TrackerRemote) registry.lookup("tracker");
        
        this.rand = new Random();
        
        String ipAddr = Common.getLocalAddress();
        if (ipAddr == null) {
            LOGGER.severe("Cannot get ip address for " + playerID);
            return;
        }
        this.myPlayerAddr = new PlayerAddr(ipAddr, DEFAULT_PORT, playerID);
        this.myPlayerAddr.playerID = playerID;
        
        Common.registerGame(this);
    }



    /******   for primary server only  ******/
    // used when other player wants to join the game
    // the param and returned type for this method is not carefully considered yet
    public GameState addOtherPlayer(PlayerAddr playerAddr){
        // TODO: shall we make this method synchronized since RMI remote call is multi-thread?

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

        if (this.playerAddrMap.size() <= 1) {
            LOGGER.severe("[addOtherPlayer] invalid playerAddrMap size");
        } else if (this.playerAddrMap.size() == 2) {
            // promote this player to backup    
            // after this player calles updateGameState, it will starts behave as backup
            backupPlayerID = playerAddr.playerID;
            gameState.isBecomeBackup = true;
        } else {
            boolean ok = updateBackup();
            if (!ok) {
                // current backup is dead, we should find another one
                LOGGER.warning("update backup fail");
                // TODO: recover backup
                // (Let's just let the helper thread do this job and see if it works)
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
            // (Let's just let the helper thread do this job and see if it works)
        }
    
        udpateGameInterface();
        return prepareGameState();
    }


    // called by primary server to update gameState to backup
    // 
    private boolean updateBackup() {    
        if (this.backupPlayerID == ""){
            LOGGER.info("[updateBackup] backup not exist. skiped");
            return true;
        }

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
            //       so I've commented out the following line
            // Common.handleError(registry, remote, backupPlayerID, e);
            LOGGER.warning("update backup fail!");
            return false;
        }
        
    }

    // called by primary server itself to promote another server to backup
    // Currently it is called when
    //   1. primary helper discover backup is dead while pinging backup
    //   2. backup server helper finds primary dead, it promotes itself to be primary 
    //      then try to promote another as backup
    // note:
    //  there are 2 other cases where we should promote another server to backup
    //  a) there is no backup and one player asks to join
    //     this case should be handled by primary setting gameState.isBecomeBackup=true
    //      and the new player updateGameState to apply the change
    //  b) backup server exit
    //     this case is handled by applyPlayerExit function
    public void promoteSomeoneToBackup(){
        // TODO: synchronize method

        // empty the current backup playerID
        // if the promotion fails, 
        // then we will have no backup instead of wrong backup
        this.backupPlayerID = "";

        GameState gameState = prepareGameState();
        gameState.isBecomeBackup = true;
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
                        // this will promote this player to be backup and also update gamestate 
                        // here we assume the backup already has the correct knowledge of primaryPlayerID
                        playerStub.updateGameState(gameState);
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
                //       let's leave it to the primaryHelper?
            }
        }
    }

    // this method is used by the primary server (not remote call)
    // to remove a player that is already dead 
    //  a) a dead primary server (when promoting self to primary)
    //  b) a dead backup server (in primary helper thread)
    //  c) a dead normal server (in primary helper thread)
    // make sure you know the following side effect before using this method
    // side effect 1: if backup is removed, it will call promoteSomeoneToBackup
    // side effect 2: if others are remove, it will call updateBackup
    public void forceRemovePlayer(String playerID){
        applyPlayerExit(playerID);
        // TODO: again this is a place where we may think of removing the player from the tracker
        //       if the "remove player from the tracker only upon new player join game" strategy has problem
    }

    /******  End of for primary server only  ******/

    


    /******  for backup server only  ******/

    // promote self to become primary and notify other nodes
    // currently this is only used when
    //  a) discover primary dead when pinging primary
    // But we should think about whether the same mechanism applies when
    //  b) primary exit
    public void promoteSelfToPrimary(){
        // TODO synchronize
 
        // 1.1 update setting to make self primary
        this.gameRole = PRIMARY;
        this.primaryPlayerID = myPlayerAddr.playerID;
        this.backupPlayerID = "";

        // 1.2 remove the old primary from gamestate
        this.forceRemovePlayer(this.primaryPlayerID);

        GameState gameState = prepareGameState();
        gameState.shouldChangePrimary = true;
        gameState.primaryPlayerID = this.primaryPlayerID;
        // 2. notify other players
        for (Map.Entry<String, PlayerAddr> entry : playerAddrMap.entrySet()) {
            String playerID = entry.getKey();
            PlayerAddr playerAddr = entry.getValue();

            if (playerID == this.primaryPlayerID){
                continue;
            }

            boolean updateSucceeded = false;
            try {               
                if (playerAddr == null){
                    LOGGER.warning("Impossible! we got null value by iterating a map! playerID: "+playerID);
                } else {
                    GameRemote playerStub = getPlayerStub(playerAddr);                
                    if (playerStub != null){  
                        playerStub.updateGameState(gameState);
                        // if we can reach here without throwing exception, 
                        // the promotion is successful!
                        updateSucceeded = true;
                    }      
                }                               
            } catch (Exception e) {
                // do nothing
            } 

            if (!updateSucceeded){
                // TODO: again we should decide whether to deal with the dead player
            } 
        }

        // 3. promote another to be backup if possible
        // there is no gurantee that we can find somebody to promote
        // the reason to put it after update all other players
        // is that we want all other player to have the correct knowledge of primaryPlayerID
        this.promoteSomeoneToBackup();

        
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

    // called by primary server to update backup server state
    // previously this method is for backup server only,
    // now I'm using it for normal server also
    public void updateGameState(GameState gameState){
        LOGGER.info("[updateGameState] update player size: " + gameState.playerAddrMap.size());
        maze = gameState.maze;
        playerCoordMap = gameState.playerCoordMap;
        playerScores = gameState.playerScores;
        playerAddrMap = gameState.playerAddrMap;
        if (gameState.isBecomeBackup) {
            promoteSelfToBackup();
        }

        if (gameState.shouldChangePrimary){
            this.primaryPlayerID = gameState.primaryPlayerID;
        }
        if (gameState.shouldChangeBackup){
            this.primaryPlayerID = gameState.backupPlayerID;
        }

        udpateGameInterface();
    }

    // you should call this method when you're sure that 
    // the players knows the correct primaryPlayer
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
                (new Thread(new PrimaryHelper(this))).start();
                LOGGER.info("join game succeeded");

            } else {

                PlayerAddr primaryServerAddr = contactPlayer(response.playerAddr);
                if (primaryServerAddr == null){
                    LOGGER.warning("get primaryServerAddr null, something is very very wrong!");
                    Thread.sleep(SLEEP_PERIOD);
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
            LOGGER.warning("another player with id: "+ playerAddr.playerID+" uncontactable! " + e);
            isUncontactable = true;
        }

        if (isUncontactable) {
            LOGGER.info("fail to contact the contact player, retry...");
            trackerStub.removePlayerAddr(playerAddr);
            return null;
        }
        if (primaryServerAddr == null) {
            //       this is weird. If you can contact the player, 
            //       but you don't get primary server
            //       It means the program has a bug!
            //       retrying doesn't help
            LOGGER.warning("get primaryServerAddr null, something is very very wrong!");
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
                // TODO: shall we let the tracker know we have exited?
                while (true) {
                    boolean exitSucceeded = false;
                    switch (this.gameRole){
                        case NORMAL:
                            // call the primary server to exit
                            // if uncontactable, just sleep for a while to get notified the new primary
                            if (remoteApplyMove(EXIT) != null){
                                exitSucceeded = true;
                            } else {
                                LOGGER.warning("[move] remoteApplyExit fail, retry");
                            }
                        break;
                        case BACKUP:
                            // call the primary server to exit
                            // if uncontactable, it is likely that the game is undergoing critical period,
                            // just sleep and retry (next time I'm mostly likely to be the primary server)
                            if (remoteApplyMove(EXIT) != null){
                                exitSucceeded = true;
                            } else {
                                LOGGER.warning("[move] remoteApplyExit fail, retry");
                            }
                        break;
                        case PRIMARY:
                            // TODO: now we exit without doing anything                        
                            //       hoping the backup helper thread to discover in tiem
                            //       but maybe it's better that we do some extra work
                            //       like promoting another primary&backup before shutting down
                            //       because if backup crashes when we exit, the game is over!
                            LOGGER.info("[move] primary exit");
                            exitSucceeded = true;
                        break;
                        default:
                            System.out.println("wrong role type");
                        break;
                    }

                    if (exitSucceeded){
                        break;
                    }

                    // sleep and retry
                    // I've move out the sleep and retry logic to this place
                    // because I want the retry to re-switch the role
                    Thread.sleep(SLEEP_PERIOD);
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

    // currently this function assumes playerID != primaryPlayerID
    private GameState applyPlayerExit(String playerID) {
        Coord coord = playerCoordMap.get(playerID);
        maze[coord.x][coord.y] = EMPTY;
        playerAddrMap.remove(playerID);
        playerCoordMap.remove(playerID);
        playerScores.remove(playerID);

        if (playerID == backupPlayerID){
            // backup exit, we need to find another one
            promoteSomeoneToBackup();
        } else {
            boolean ok = updateBackup();
            if (!ok){
                //TODO: recover backup
                // Let's leave it to the helper thread and see whether it works well
                // return null;
            }
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