import java.util.logging.Logger;

public class BackupHelper implements Runnable  {
	private static final int SLEEP_PERIOD = 100;	

	// TODO: any place that access backupPlayer may have concurrent access issue.
	// think about how to solve it
	Game backupPlayer;

	private final Logger LOGGER = Logger.getLogger("BackupHelper");

	public BackupHelper(Game backupPlayer){
		this.backupPlayer = backupPlayer;
	}

	public void run() {
		// keep pinging primary server until 
		// 	1. primary server uncontactable and we deal with it
		//  2. backupPlayer uncontactable and we shut down
		while (true) {
			try{
				Thread.sleep(SLEEP_PERIOD);
			} catch (Exception e){
				// TODO: log it
			}	

			// TODO: using remote call to detect self dead may be wrong!
			// 		 since helper is only a thread, 
			// 		 maybe we don't even have to worry about this?
		    boolean backupUncontactable = false;
		    try {		    	
                GameRemote backupPlayerStub = backupPlayer.getPlayerStub(backupPlayer.myPlayerAddr);                
                if (backupPlayerStub == null){
                    
                    backupUncontactable = true;
                } else {
                    backupPlayerStub.ping();    
                }                    
            } catch (Exception e) {
                backupUncontactable = true;
            } 			
            
            if (backupUncontactable){
				LOGGER.info("backupPlayerStub not contactable. shutting down...");
            	return; 
            }

			boolean primaryUncontactable = false;
		    try {		    	
		    	PlayerAddr primaryAddr = backupPlayer.playerAddrMap.get(backupPlayer.primaryPlayerID);
		    	if (primaryAddr == null){
		    		LOGGER.warning("BUGGY! fail to get primary server address from backupPlayer.playerAddrMap");
		    		primaryUncontactable = true;
		    	} else {
		    		GameRemote primaryPlayerStub = backupPlayer.getPlayerStub(primaryAddr);                
	                if (primaryPlayerStub == null){	                    
	                    primaryUncontactable = true;
	                } else {
	                    primaryPlayerStub.ping();    
	                }    	
		    	}	                            
            } catch (Exception e) {
                primaryUncontactable = true;
            } 			
            
            if (!primaryUncontactable){
				continue;
            }            

			LOGGER.info("primaryUncontactable not contactable. promoting self to primary");            
			backupPlayer.promoteSelfToPrimary();
			// note that the old primary should be remove from tracker during promoteSelfToPrimary
			LOGGER.info("finish promoting self to primary. backupHelper shutting down...");
			return;

		}

	}

}