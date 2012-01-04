package bpipe

import java.util.logging.Logger;

/**
 * Implementation of support for TORQUE resource manager.
 * <p>
 * The actual implementation is a shell script written by 
 * Bernie Pope <bjpope@unimelb.edu.au>.  This is just a 
 * wrapper class that provides access to it via the
 * CustomJob parent class support for shell script job
 * managers.
 * 
 * @author simon.sadedin@mcri.edu.au
 */
class TorqueCommandExecutor extends CustomCommandExecutor implements CommandExecutor {

    public static final long serialVersionUID = 0L

    /**
     * Logger for this class to use
     */
    private static Logger log = Logger.getLogger("bpipe.TorqueCommandExecutor");

    /**
     * Child processes that are forwarding output from this torque command
     */
    transient List<Process> forwarders = []

    /**
     * Constructor
     */
    TorqueCommandExecutor() {
        super(new File(System.getProperty("bpipe.home") + "/bin/bpipe-torque.sh"))
    }

    /**
     * Adds forwarding of standard err & out for processes started using Torque.
     * These appear as files in the local directory.
     */
    @Override
    public void start(String name, String cmd) {
        
        // TODO: possibly wrap command in here so that we catch input / output?
        super.start(name, cmd);
        
        // After starting the process, we launch a background thread that waits for the error
        // and output files to appear and then forward those inputs
        forward(this.name+".o"+this.commandId, System.out)
        forward(this.name+".e"+this.commandId, System.err)
        
        // Start another background thread to wait for our job to complete and cleanup the outputs
        // when it does
        new Thread({
            while(status() != CommandStatus.COMPLETE.name()) {
                Thread.sleep(5000)
            }
            cleanup()
        }).start()
    }

    private void forward(String fileName, OutputStream s) {
        File f = new File(fileName)
        new Thread({
            while(true) {
                if(f.exists())
                    break
                Thread.sleep(200)
            }
            log.info "Starting forward of output to file $f.absolutePath"
            Process p = Runtime.runtime.exec("tail -f $f.absolutePath")
            this.forwarders << p
            p.consumeProcessOutputStream(s)
        }).start()
    }

    /**
     * Adds custom cleanup of torque created files and stop any threads forwarding output 
     */
    @Override
    public void stop() {
        super.stop();
        cleanup()
    }

	private cleanup() {
		this.forwarders*.destroy()
		File of = new File(this.name+".o"+this.commandId)
		if(of.exists())
			of.delete()
		File ef = new File(this.name+".e"+this.commandId)
		if(ef.exists())
			ef.delete()
	}

    /**
     * The torque script / system produces files like BpipeJob.o133722 and 
     * BpipeJob.e133722 that contain standard output and error.  We don't want
     * these to be considered as result files from jobs so return a mask
     * that screens them out.
     */
    List<String> getIgnorableOutputs() {
        return [ this.name + '.o.*$', this.name + '.e.*$' ]
    }

    String toString() {
        "Torque Job [" + super.toString() + "]"
    }
}
