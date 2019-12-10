package bpipe;

/**
 * Possible values for Jobs that Bpipe is running
 * 
 * @author simon.sadedin@mcri.edu.au
 */
public enum CommandStatus {
    QUEUEING,
    WAITING,
    RUNNING, 
    COMPLETE, 
    EXITING, 
    UNKNOWN
}
