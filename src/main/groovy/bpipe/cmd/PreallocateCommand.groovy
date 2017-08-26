/*
 * Copyright (c) 2016 MCRI, authors
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
package bpipe.cmd

import java.io.Writer;
import java.util.List

import javax.swing.colorchooser.CenterLayout

import bpipe.CommandManager;
import bpipe.Config
import bpipe.ExecutorFactory
import bpipe.ExecutorPool
import bpipe.PooledExecutor
import bpipe.Runner
import groovy.transform.CompileStatic;
import groovy.util.logging.Log;

/**
 * Start any persistent preallocated commands that are configured in
 * the bpipe.config without starting the actual pipeline itself
 * 
 * @author Simon Sadedin
 */
@Log
class PreallocateCommand extends BpipeCommand {
    
    public PreallocateCommand(List<String> args) {
        super("preallocate", args);
    }

    @Override
    public void run(Writer out) {
        Config.config["mode"] = "preallocate"
        
        out.println ""
        
        Runner.readUserConfig()
        
        if(!('preallocate' in Config.userConfig)) {
            out.println "No preallocate section was found in configuration (bpipe.config). To preallocate resources,"
            out.println "first declare a preallocate section in your configuration."
            out.println ""
            System.exit(0)
        }
        
        out.println "Starting resources in preallocate configuration ... \n"
        
        try {
            this.startPools(out)
        }
        catch(Exception e) {
            out.println """
                An error occurred starting one or more of the preallocated executors. The following messages may help to understand the problem:
                
                ${e.message}
            """.stripIndent()
        }
      
        System.exit(0)
    }
    
    void startPools(Writer out) {
        int poolsStarted = ExecutorPool.startPools(ExecutorFactory.instance, Config.userConfig, true, true)
            
        if(poolsStarted == 0) {
            out.println "Although a preallocate section was specified, no preallocated jobs were started."
            out.println ""
            out.println "This may indicate that none of the pools were marked as persistent. Only persistent"
            out.println "pools can be preallocated prior to running a pipeline."
            out.println ""
            System.exit(0)
        }
        out.println "${poolsStarted} persistent pools were started."
        out.println ""
    }
}
