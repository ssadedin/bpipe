package bpipe

/**
 * Implementation of a command executor that executes 
 * commands through a local Bash shell.
 * 
 * @author simon.sadedin@mcri.edu.au
 */
class LocalCommandExecutor implements CommandExecutor {
    
    Process process
    
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
    
    void start(String name, String cmd) {
      new Thread({
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
