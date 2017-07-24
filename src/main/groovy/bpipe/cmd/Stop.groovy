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
import bpipe.CommandStatus
import bpipe.Config
import bpipe.ExecutorFactory
import bpipe.ExecutorPool
import bpipe.PooledExecutor
import groovy.transform.CompileStatic;
import groovy.util.logging.Log;

/**
 * Stop the jobs associated with the currently running pipeline
 * 
 * @author Simon Sadedin
 */
@Log
class Stop extends BpipeCommand {
    
    public Stop(List<String> args) {
        super("stop", args);
    }

    @Override
    @CompileStatic
    public void run(Writer out) {
        Config.config["mode"] = "stopcommands"
        
        println ""
        
        if(args && (args[0] == "preallocated")) {
            stopPooledCommands()
            return
        }
        
        // Find the pid of the running Bpipe instance
        String pid = getLastLocalPID()
        
        if(pid == "-1") {
            out.println "No bpipe pipeline found running in this directory."
            return
        }
        
        if(isRunning(pid)) {
            out.println "Stopping Bpipe process $pid"
            
            // Try to kill the bpipe process (ourselves!)
            String output = ("kill $pid").execute().text
            if(output)
                out.println "Output from kill: $output"
        }
        
        // Give it a tiny window to happen
        Thread.sleep(100)
            out.println "Sending stop signal to commands"
                
        int count = new CommandManager().stopAll()
        out.println "Stopped $count commands"
    }
    
    void stopPooledCommands() {
        
       Config.readUserConfig()
        
       ExecutorPool.initPools(ExecutorFactory.instance, Config.userConfig) 
       
       ExecutorPool.pools*.value.each { ExecutorPool pool ->
           
           List<PooledExecutor> pes = pool.searchForExistingPools()
           
           println " Pool $pool.cfg.name ".center(Config.config.columns, "=")
           if(pes.size() > 0) {
               println ""
               for(PooledExecutor pe in pes) {
                   String oldState = pe.executor.status()
                   try {
                       pe.stopPooledExecutor()
                       pe.deletePoolFiles()
                       String newState 
                       int retries = 0
                       while(true) {
                           newState = pe.executor.status()
                          if(newState != CommandStatus.RUNNING.name()) 
                              break
                              
                          if(++retries > 5) {
                              log.warning("Command $pe.command.id did not respond to stop command")
                              break
                          }
                              
                          Thread.sleep(1000)
                          
                       } 
                       
                       println "Pool: $pool.cfg.name".padLeft(20) + " Command: ${pe.command.id}".padLeft(15) + " State: ${oldState} => ${newState}"
                   }
                   catch(Exception e) {
                       println "ERROR: Stop command for pool job $pe.hostCommandId returned error: $e"
                   }
               }
           }
           else {
               println "\nNo active preallocated commands were found"
           }
       }
       println ""
       println "".center(Config.config.columns, "=")
       println "\nDone.\n"
    }
}
