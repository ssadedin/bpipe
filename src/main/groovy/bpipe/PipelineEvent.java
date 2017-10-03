package bpipe;

/**
 * Events that can occur during execution of a pipeline.
 * Used by NotificationChannel to allow notifications to appear
 * for particular events.
 * 
 * @author ssadedin
 */
public enum PipelineEvent {
    
    /* ----------------- Pipeline Level Events ----------------------- */
    
	/**
	 * Supported
	 */
	STARTED,
	
	/**
	 * Supported
	 */
	FINISHED,
	
	/**
	 * Supported
	 */
	FAILED,
	
	/**
	 * Future
	 */
	SUCCEEDED,
	
	/**
	 * Supported
	 */
	STAGE_STARTED,
	
    /* ----------------- Stage Level Events ----------------------- */
    
	/**
	 * Stage has completed execution
     * <p>
	 * Note this is sent regardless of success or failure. To listen for
	 * failure, see {@link #STAGE_FAILED}.
	 */
	STAGE_COMPLETED,
	
	/**
	 * Future
	 */
	STAGE_FAILED,
    
    /**
     * Bpipe is exiting - Supported
     */
	SHUTDOWN,
    
	/* ----------------- Checks --------------------------------------*/
    CHECK_EXECUTED,
    
    CHECK_SUCCEEDED,
    
    CHECK_FAILED,
    
    CHECK_OVERRIDDEN,
	
    /* ----------------- Command Level Events ----------------------- */
    
	/**
	 * A command is being checked to see if it needs
	 * to be executed. eg. are inputs older than outputs?
	 */
	COMMAND_CHECK,
	
	/**
	 * Future
	 */
	COMMAND_STARTED,
	
	/**
	 * Future
	 */
	COMMAND_FINISHED,
	
	/**
	 * Future
	 */
	COMMAND_FAILED,
	
	
	/**
	 * A report has been generated (supported)
	 */
    REPORT_GENERATED,
    
    /**
     * The user generated a 'SEND' event themselves with the 'send' command
     */
    SEND
}
