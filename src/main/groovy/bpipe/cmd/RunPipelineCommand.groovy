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
package bpipe.cmd

import bpipe.Utils
import groovy.transform.CompileStatic
import groovy.util.logging.Log

@Log
class RunPipelineCommand extends BpipeCommand {
    
    public RunPipelineCommand(List<String> args) {
        super("run", args);
    }
    
    @Override
    public void run(Writer out) {
        
        if(dir == null) 
            throw new IllegalArgumentException("Directory parameter not set. Directory for command to run in must be specified")
        
        File dirFile = new File(dir).absoluteFile
        
        // This was mainly for security, but is actually problematic because we need pipeline directories two deep at times
        // TODO: figure out how to limit / constrain this - an entry in agent config?
        //
        // if(!dirFile.parentFile.exists())
        //   throw new IllegalArgumentException("Directory supplied $dir is not in an existing path. The directory parent must already exist.")
        //        
        if(!dirFile.exists() && !dirFile.mkdirs())
            throw new IllegalArgumentException("Unable to create directory requested for pipeline run $dir.")
        
        log.info "Running with arguments: " + args;
        
        List<String> cmd = [ bpipe.Runner.BPIPE_HOME + "/bin/bpipe", "run" ] 
        cmd.addAll(args)
        Map result = Utils.executeCommand(cmd, out:out, err: out) {
            directory(dirFile)
        }
    }
}

