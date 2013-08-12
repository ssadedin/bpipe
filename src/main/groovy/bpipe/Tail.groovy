/*
 * Copyright (c) Murdoch Childrens Research Institute and Contributers
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * Redistributions in binary form must reproduce the above copyright notice, this
 * list of conditions and the following disclaimer in the documentation and/or
 * other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package bpipe

import java.io.BufferedReader;

/**
 * Custom support for tailing the log file.
 * <p>
 * This job used to be done by using the 'tail' command directly,
 * but now that the log files contain more meta data that approach 
 * became impractical, especially since problems with buffering 
 * make it extremely complicated to pipe tail output to other commands.
 * 
 * @author simon.sadedin@mcri.edu.au
 */
class Tail {
    
    final static int DEFAULT_LINES = 72
    
    static CliBuilder logCli = new CliBuilder(usage: "log <options> [<jobid>]", posix: true)
    
    static void main(String [] args) {
        logCli.with {
            n longOpt: 'lines', 'number of lines to log', args:1
            t longOpt: 'threads', 'thread id to track', args:1
            f longOpt: 'follow', 'keep following file until user presses Ctrl+C'
        }
        
        def opts = logCli.parse(args)
        
        int lines = opts.n ? opts.n.toInteger() : DEFAULT_LINES
        String threadId = opts.t ?: null
        
        if(opts.arguments().size()==0) {
            logCli.usage()
            System.exit(1)
        }
        
        String jobId = opts.arguments()[0]
        
        // Open the file
        File logFile = new File(".bpipe/logs/${jobId}.log")    
        
        int countSkip = 0
        BufferedReader r 
        try {
          int backwardsBytes = lines*250
          List buffer = []
          while(buffer.size()<lines) {
              
            if(r != null) {
                r.close()
                backwardsBytes = backwardsBytes * 2
                if(backwardsBytes > logFile.length())
                    break
            }
            
            r = new BufferedReader(new FileReader(logFile))
            int skipLength = logFile.length() - backwardsBytes
            if(skipLength>0) {
              r.skip(skipLength)
            }
          
            // Attempt to read the requested number of lines
            while(true) {
                
                String line = r.readLine()
                if(line == null) {
                    break
                }
                
                // Extract the thread id
                int tabIndex = line.indexOf('\t')
                String log = line
                if(tabIndex > 0) {
                  String metaData = line.substring(0, tabIndex)
                  log = line.size() > tabIndex+1 ? line.substring(tabIndex+1) : ""
                  if(threadId && metaData.indexOf(threadId)!=1) 
                      continue
                }
                buffer << log
                if(buffer.size() > lines)
                    buffer.remove(0)
            }
          }
          
          buffer.each { println(it) }
          
          if(!opts.f)
              System.exit(0)
          
          while(true) {
              String line = r.readLine()
              if(line == null) {
                  Thread.sleep(1000)
              }
              else {
                int tabIndex = line.indexOf('\t')
                String metaData = line.substring(0, tabIndex)
                if(threadId && metaData.indexOf(threadId)!=1)
                    continue
                println line.substring(line.indexOf('\t')+1)
              }
          }
        }
        finally {
            r.close()
        }
    }
}
