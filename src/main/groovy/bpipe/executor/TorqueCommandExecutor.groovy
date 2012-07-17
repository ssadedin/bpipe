/*
* Copyright (c) 2012 MCRI, authors
*
* Permission is hereby granted, free of charge, to any person
* obtaining a copy of this software and associated documentation
* files (the "Software"), to deal in the Software without
* restriction, including without limitation the rights to use,
* copy, modify, merge, publish, distribute, sublicense, and/or sell
* copies of the Software, and to permit persons to whom the
* Software is furnished to do so, subject to the following
* conditions:
*
* The above copyright notice and this permission notice shall be
* included in all copies or substantial portions of the Software.
*
* THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
* EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
* OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
* NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
* HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
* WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
* FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR
* THE USE OR OTHER DEALINGS IN THE SOFTWARE.
*/
package bpipe.executor

import java.util.logging.Logger
import bpipe.ForwardHost;

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
@Mixin(ForwardHost)
class TorqueCommandExecutor extends CustomCommandExecutor implements CommandExecutor {

    public static final long serialVersionUID = 0L

    /**
     * Logger for this class to use
     */
    private static Logger log = Logger.getLogger("bpipe.executor.TorqueCommandExecutor");

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
    public void start(Map cfg, String id, String name, String cmd, File outputDirectory) {
        
        super.start(cfg, id, name, cmd, outputDirectory);
        
        // After starting the process, we launch a background thread that waits for the error
        // and output files to appear and then forward those inputs
        forward(this.jobDir+"/${id}.out", System.out)
        forward(this.jobDir+"/${id}.err", System.err)
    }

    /**
     * Adds custom cleanup of torque created files and stop any threads forwarding output 
     */
    @Override
    public void stop() {
        super.stop();
        cleanup()
    }

	void cleanup() {
		this.forwarders*.cancel()
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
