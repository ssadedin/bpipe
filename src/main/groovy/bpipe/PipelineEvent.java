package bpipe;

/**
 * Events that can occur during execution of a pipeline.
 * Used by NotificationChannel to allow notifications to appear
 * for particular events.
 * 
 * @author ssadedin
 */
public enum PipelineEvent {
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
	
	/**
	 * Supported
	 */
	STAGE_COMPLETED,
	
	/**
	 * Future
	 */
	STAGE_FAILED,
	
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
	COMMAND_FAILED
}
