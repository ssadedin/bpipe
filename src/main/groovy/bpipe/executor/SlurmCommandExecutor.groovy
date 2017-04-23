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

/* Modified from TorqueCommandExecutor.groovy for Slurm 
*
* Approach is mimic the wrapper and shell script relationship, and replace 
* Torque commands with Slurm equivalents
*/

package bpipe.executor

import groovy.util.logging.Log
import bpipe.Command;
import bpipe.ForwardHost;
import bpipe.PipelineError
import bpipe.Utils

/**
 * Implementation of support for Slurm resource manager.
 * <p>
 * This class adapts the Torque implementation (which is similar) to work
 * with Slurm. The only differences are that some environment variables are
 * set differently in passing through to the bpipe-slurm.sh wrapper.
 * 
 * @author simon.sadedin@mcri.edu.au
 * @author andrew.lonsdale@lonsbio.com.au
 * @author slugger70@gmail.com
 */
@Mixin(ForwardHost)
@Log
class SlurmCommandExecutor extends TorqueCommandExecutor implements CommandExecutor {

    public static final long serialVersionUID = 0L

    /**
     * Constructor
     */
    SlurmCommandExecutor() {
        super(new File(System.getProperty("bpipe.home") + "/bin/bpipe-slurm.sh"))
    }
    
    @Override
    void setEnvironment(Map env) {
        super.setEnvironment(env)
        
        if(config?.memory)
            env.MEMORY = String.valueOf(config.memory)
        
        // jobtype since queues (parition in slurm) may not determine computation mode
        if(config?.jobtype) {
            log.info "Using jobtype: $config?.jobtype"
            env.JOBTYPE = config.jobtype
        }

        //modules since we may need to load modules before the command... - Simon Gladman (slugger70) 2014
        if(config?.modules) {
            log.info "Using modules: $config?.modules"
            env.MODULES = config.modules
        }


    }

    void cleanup() {
        this.stopForwarding()
        // slurm12 is stdout and stderr
        File slurm12= new File("slurm-"+this.commandId+".out")
        if(slurm12.exists())
            slurm12.delete()
    }

    /**
     * The slurm script / system produces a file slurm-SLURMID.out with both stderr and
     * stdout. We don't want
     * these to be considered as result files from jobs so return a mask
     * that screens them out.
     */
    @Override
    List<String> getIgnorableOutputs() {
        return ["slurm-[0-9]*.out"]
    }

    String toString() {
        "Slurm Job [" + "Command Id: $commandId " + (config?"Configuration: $config":"") + "]"
    }
}
