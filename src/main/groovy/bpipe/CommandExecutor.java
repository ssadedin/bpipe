package bpipe;

/**
 * Abstract class that represents a shell command to be executed
 * by Bpipe.  How the command is executed is implementation dependent.
 * <p>
 * Note: the stop() command is not part of this interface because
 * it is not possible to reliably stop a local process job using Java.  
 * This is because Java doesn't give access to the process hierarchy so that 'child'
 * processes can be chased down.  Implementations still need to provide a 
 * "stop" command.
 * 
 * @author simon.sadedin@mcri.edu.au
 */
interface CommandExecutor {
    
    void start(String cmd);
    
    String status();
    
    int waitFor(); 
}
