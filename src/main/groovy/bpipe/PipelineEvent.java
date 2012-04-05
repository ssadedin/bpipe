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
	 * Future
	 */
	STAGE_STARTED,
	
	/**
	 * Future
	 */
	STAGE_COMPLETED,
	
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
