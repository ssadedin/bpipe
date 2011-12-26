package bpipe

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
    TorqueCommandExecutor() {
        super(new File(System.getProperty("bpipe.home") + "/bin/bpipe-torque.sh"))
    }
}
