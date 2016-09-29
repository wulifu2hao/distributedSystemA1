import java.util.logging.Logger;
import java.util.HashSet;
import java.util.Set;
import java.util.Map;
import java.util.Iterator;

public class PrimaryHelper implements Runnable  {
	private static final int SLEEP_PERIOD = 100;	

	// TODO: any place that access primaryPlayer may have concurrent access issue.
	// think about how to solve it
	Game primaryPlayer;
	String logtag;

	private final Logger LOGGER = Logger.getLogger("primaryPlayer");

	public PrimaryHelper(Game primaryPlayer){
		this.primaryPlayer = primaryPlayer;
		this.logtag = "[primary helper of "+primaryPlayer.myPlayerAddr.playerID+"] ";
	}

	public void run() {
		// keep pinging everybody 
		LOGGER.info(logtag+"starts running");
		while (true) {
			try{
				Thread.sleep(SLEEP_PERIOD);
			} catch (Exception e){
				LOGGER.warning(logtag+"sleep is interupted!");
			}	

			// TODO: using remote call to detect self dead may be wrong!
			// 		 since helper is only a thread, 
			// 		 maybe we don't even have to worry about this?
			// 		 anyway I don't think this will cause problem
		    boolean selfUncontactable = false;
		    try {		    	
                GameRemote primaryPlayerStub = primaryPlayer.getPlayerStub(primaryPlayer.myPlayerAddr);                
                if (primaryPlayerStub == null){                   
                    selfUncontactable = true;
                } else {
                    primaryPlayerStub.ping();    
                }                    
            } catch (Exception e) {
                selfUncontactable = true;
            } 			
            
            if (selfUncontactable){
				LOGGER.info(logtag+"primaryPlayerStub not contactable. shutting down...");
            	return; 
            }

            // if has backupPlayer, let's ping it to check whether it's alive
            if (!primaryPlayer.backupPlayerID.equals("")) {
				boolean backupUncontactable = false;
			    try {		    	
			    	PlayerAddr backupAddr = primaryPlayer.playerAddrMap.get(primaryPlayer.backupPlayerID);
			    	if (backupAddr == null){
			    		LOGGER.warning(logtag+"BUGGY! fail to get backup server address of id: "+primaryPlayer.backupPlayerID+" from primaryPlayer.playerAddrMap");
			    		backupUncontactable = true;
			    	} else {
			    		GameRemote backupPlayerStub = primaryPlayer.getPlayerStub(backupAddr);                
		                if (backupPlayerStub == null){	                    
		                    backupUncontactable = true;
		                } else {
		                    backupPlayerStub.ping();    
		                }    	
			    	}	                            
	            } catch (Exception e) {
	                backupUncontactable = true;
	            } 

	            if (backupUncontactable){
	            	LOGGER.info(logtag+"backupUncontactable. removing backup: "+primaryPlayer.backupPlayerID);
	            	// remove this player from gamestate as well as tracker info
	            	primaryPlayer.forceRemovePlayer(primaryPlayer.backupPlayerID);

					// promote somebody to be backup
					// this step gurantees
					// a) the old backup is remove, i.e. primaryPlayer.backupPlayerID = ""
					// b) if the exist another player alive, he will become backup
					// 	  i.e. the only possibility that we fails to promote a backup shoud be 
					// 		   because we are the only player left in the game
					// LOGGER.info(logtag+"attempt to promote someone to backup...");
					// primaryPlayer.promoteSomeoneToBackup();
	            } 

            }

            // let's now ping other players to make sure they are alive 
            Set<String> deadPlayerSet = new HashSet<>();
            for (Map.Entry<String, PlayerAddr> entry : primaryPlayer.playerAddrMap.entrySet()) {
			    String playerID = entry.getKey();
			    PlayerAddr playerAddr = entry.getValue();

			    if (playerID.equals(primaryPlayer.myPlayerAddr.playerID) || playerID.equals(primaryPlayer.backupPlayerID)) {
			    	// no point checking them, we are only interested in normal players
			    	continue;
			    }

			    boolean playerUncontactable = false;
			    try {		    	
			    	if (playerAddr == null){
			    		LOGGER.warning(logtag+"Impossible! we got null value by iterating a map! playerID: "+playerID);
			    		playerUncontactable = true;
			    	} else {
			    		GameRemote playerStub = primaryPlayer.getPlayerStub(playerAddr);                
		                if (playerStub == null){	                    
		                    playerUncontactable = true;
		                } else {
		                    playerStub.ping();    
		                }    	
			    	}	                            
	            } catch (Exception e) {
	                playerUncontactable = true;
	            } 

				if (playerUncontactable){
					deadPlayerSet.add(playerID);
					LOGGER.info(logtag+"dead player detected: "+playerID);
	            } 
			}

			// remove the dead players
			Iterator<String> iter = deadPlayerSet.iterator();
			while (iter.hasNext()) {
				String deadPlayerID = iter.next();
				LOGGER.info(logtag+"removing player "+deadPlayerID);
				primaryPlayer.forceRemovePlayer(deadPlayerID);
			}


			// By definition primary server should keep running untill it crash/exit
			// so in no case that we should break out of the loop here
		}

	}

}