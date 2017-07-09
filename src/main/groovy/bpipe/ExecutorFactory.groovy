/*
 * Copyright (c) 2017 MCRI, authors
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
package bpipe

import bpipe.executor.CustomCommandExecutor
import groovy.util.logging.Log
import bpipe.executor.CommandExecutor

@Singleton
@Log
class ExecutorFactory {
    
    CommandExecutor createExecutor(Map cfg) {
        
        // Try and resolve the executor several ways
        // It can be a file on the file system,
        // or it can map to a class
        
        CommandExecutor cmdExec = null
        def executor = cfg.executor
        
        // Already assigned
        if(executor instanceof CommandExecutor)
            return executor
        
        if((executor instanceof String) && new File(executor).exists()) {
            File executorFile = new File(executor)
            cmdExec = new CustomCommandExecutor(executorFile)
        }
        else
        if(executor instanceof Closure) {
            return executor()
        }
        else {
            String name1 = "bpipe.executor."+executor.capitalize()
            
            /* find out the command executor class */
            Class cmdClazz = null
            try {
                cmdClazz = Class.forName(name1)
            }
            catch(ClassNotFoundException e) {
                String name2 = "bpipe.executor."+executor.capitalize() + "CommandExecutor"
                try {
                    cmdClazz = Class.forName(name2)
                }
                catch(ClassNotFoundException e2) {
                    log.info("Unable to create command executor using class $name2 : $e2")
                    String name3 = executor
                    try {
                        cmdClazz = Class.forName(name3)
                    }
                    catch(ClassNotFoundException e3) {
                        throw new PipelineError("Could not resolve specified command executor ${executor} as a valid file path or a class named any of $name1, $name2, $name3")
                    }
                }
            }
            
            /* let's instantiate the found command executor class */
            try {
                cmdExec = cmdClazz.newInstance()
            }
            catch( PipelineError e ) {
                // just re-trow
                throw e
            }
            catch( Exception e ) {
                throw new PipelineError( "Cannot instantiate command executor: ${executor}", e )
            }
        }
        
    }

}
