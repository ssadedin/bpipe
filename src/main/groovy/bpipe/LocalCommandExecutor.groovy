package bpipe

import java.util.logging.Logger;

/**
 * Implementation of a command executor that executes 
 * commands through a local Bash shell.
 * 
 * @author simon.sadedin@mcri.edu.au
 */
class LocalCommandExecutor implements CommandExecutor {
    
    transient Process process
    
    /**
     * Logger for this class to use
     */
    private static Logger log = Logger.getLogger("bpipe.LocalCommandExecutor");	
    
    /**
     * The exit code returned by the process, only
     * available after the process has exited and
     * status() or waitFor() has been called.
     */
    Integer exitValue = null
    
    /**
     * Set to true if the process is terminated forcibly
     */
    boolean destroyed = false
    
    void start(Map cfg, String id, String name, String cmd) {
      new Thread({
          
          // Special case for Windows / Cygwin
		  // On Windows Java detects spaces in arguments and if it finds them
		  // wraps the whole argument in double quotes.  However it doesn't 
		  // escape quotes embedded in the argument body, so it actually 
		  // creates invalid arguments
          if(Utils.isWindows()) {
              // See java.lang.ProcessImpl for this test. It is not really correct
			  // but it is important for it to be the same as what is in the Java src
              String origCmd = cmd
			  if(cmd.indexOf(' ') >=0 || cmd.indexOf('\t') >=0) {
                  cmd = cmd.replaceAll(/"/, /\\"/)
			  }
              log.info "Converted $origCmd to $cmd to account for broken Java argument escaping"
		  }
          
	      process = Runtime.getRuntime().exec((String[])(['bash','-c',"$cmd"].toArray()))
	      process.consumeProcessOutput(System.out, System.err)
          exitValue = process.waitFor()
          synchronized(this) {
	          this.notifyAll()
          }
      }).start()
      while(!process) 
	    Thread.sleep(100)
    }
    
    String status() {
        if(!this.process)
            return CommandStatus.UNKNOWN
        else
        if(exitValue != null)
            return CommandStatus.COMPLETE
        else
            return CommandStatus.RUNNING
    }
    
    /**
     * Wait for this job and return exit code 0 for success
     * or non-zero for an error.
     * @return
     */
    int waitFor() {
        while(true) {
            if(status() == CommandStatus.COMPLETE.name())
                return exitValue
             synchronized(this) {
                 this.wait()
             }
        }
    }
    
    List<String> getIgnorableOutputs() {
        return null;
    }
    
    void stop() {
        // Not implemented.  Java is too stupid to stop a process it previously started,
    }
    
}
