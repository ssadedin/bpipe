/*
 * Copyright (c) 2011 MCRI, authors
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

import groovy.lang.Closure;

import java.io.FileOutputStream;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import static Utils.*


/**
 * A category that adds default Bpipe functions to closures
 * to enable basic operators such as the + operator to work.
 */
class PipelineCategory {
    
    private static Logger log = Logger.getLogger("bpipe.PipelineCategory");
    
    static Map closureNames = [:]
    
    /**
     * Map of stage name to body wrapper - a closure that should be 
     * called instead of the body, passing the body as a parameter.
     * This is how predeclared Transform and Filters work.
     */
    static Map wrappers = [:]
    
    static PipelineStage currentStage 
    
    /**
     * Joins two closures representing pipeline stages together by
     * creating wrapping closure that executes each one in turn.  This is the 
     * basis of Bpipes's + syntax for joining sequential pipeline stages.
     */
    static Object plus(Closure c, Closure other) {
        Pipeline pipeline = Pipeline.currentUnderConstructionPipeline
		Binding extraBinding = pipeline.externalBinding
        
		// What we return is actually a closure to be executed later
		// when the pipeline is run.  
        def result  = {  input1 ->
            
            currentStage = new PipelineStage(pipeline.createContext(), c)
            pipeline.stages << currentStage
            currentStage.context.setInput(input1)
            currentStage.run()
            Utils.checkFiles(currentStage.context.output)
                    
            // If the stage did not return any outputs then we assume
            // that the inputs to the next stage are the same as the inputs
            // to the previous stage
            def nextInputs = currentStage.context.nextInputs
            if(nextInputs == null)
                nextInputs = currentStage.context.@input
                
            Utils.checkFiles(nextInputs)
                
            currentStage = new PipelineStage(pipeline.createContext(), other)
            currentStage.context.@input = nextInputs
            pipeline.stages << currentStage
            currentStage.run()
            return currentStage.context.nextInputs?:currentStage.context.output
        }
        pipeline.joiners << result
        return result
    }
    
    /**
     * Provides an implicit "exec" function that pipeline stages can use
     * to run commands.  This variant blocks and waits for the 
     * shell command to exit.  If the command returns a failure exit code
     * (non zero) then an exception is thrown.
     * 
     * @see #async(Closure, String)
     */
    static void exec(Closure c, String cmd) {
      Process p = async(c,cmd)
      if(p.waitFor() != 0) {
        // Output is still spooling from the process.  By waiting a bit we ensure
        // that we don't interleave the exception trace with the output
        Thread.sleep(200)
        throw new PipelineError("Command failed with exit status != 0: \n$cmd")
      }
    }
    
    static String capture(Closure c, String cmd) {
      new File('commandlog.txt').text += '\n'+cmd
      def joined = ""
      cmd.eachLine { joined += " " + it }
      // println "Joined command is: $joined"
      
      def p = Runtime.getRuntime().exec((String[])(['bash','-c',"$joined"].toArray()))
      StringWriter outputBuffer = new StringWriter()
      p.consumeProcessOutput(outputBuffer, System.err)
      p.waitFor()
      return outputBuffer.toString()
    }
    
    /**
     * Asynchronously executes the given command by passing it to 
     * a bash shell for execution.  The exit code is not checked and
     * the command may still be running on return.  The Process object 
     * for the command that is run is returned.  Callers can use the
     * {@link Process#waitFor()} to wait for the process to finish.
     */
    static Process async(Closure c, String cmd) {
      if(Runner.opts.t)
          throw new PipelineTestAbort("Would execute: $cmd")
          
      def joined = ""
      cmd.eachLine { joined += " " + it }
//      println "Joined command is: $joined"
      
      new File('commandlog.txt').text += '\n'+cmd
      def p = Runtime.getRuntime().exec((String[])(['bash','-c',"$joined"].toArray()))
      p.consumeProcessOutput(System.out, System.err)
      return p
    }
    
    static void addStages(Binding binding) {
        binding.variables.each { 
            if(it.value instanceof Closure) {
	            log.info("Found closure variable ${it.key}")
                closureNames[it.value] = it.key
            }
        }
    }
    
    /**
     * Add all properties of type Closure belonging to the class of the given 
     * object as known (named) pipeline stages.
     * 
     * @param host
     */
    static void addStages(def host) {
        // Let's introspect the clazz to see what closure attributes it has
        log.info("Adding stages from $host")
        host.metaClass.properties.each { MetaBeanProperty p ->
            try {
                def x = p.getProperty(host)
                if(x instanceof Closure) {
                    log.info("Found pipeline stage ${p.name}")
                    PipelineCategory.closureNames[x] = p.name
                }
            }
            catch(Exception e) {
                // println "Ignoring $p ($e)"
            }
        }
    }
}
