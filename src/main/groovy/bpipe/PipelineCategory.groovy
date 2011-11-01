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
 * so that they can reference them without qualifying them
 */
class PipelineCategory {
    
    private static Logger log = Logger.getLogger("bpipe.PipelineCategory");
    
    /**
     * Map of closures to names of output files they produce
     */
    static outputs = [:]
    
    /**
     * Hack - unfortunately weird scope for closures makes the
     * map above not work when resolving embedded variable names
     */
    static lastOutput = null
    
    static lastInputs = null
    
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
                
            lastInputs = nextInputs
            
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
     * Specifies that the given output (out) will be produced
     * by the given closure, and skips execution of the closure
     * if the output is newer than all the current inputs.  
     */
    static Object produce(Closure c, Object out, Closure body) { 
        log.info "Producing $out from $currentStage.context"
        
        // Unwrap any wrapped inputs that may have been passed in the outputs
        out = Utils.unwrap(out)
        
        def lastInputs = currentStage.context.@input
        if(Utils.isNewer(out,lastInputs)) {
          msg(c,"Skipping steps to create $out because newer than $lastInputs ")
	      currentStage.context.output = out
        }
        else {
            lastOutput = out 
            
            currentStage.context.output = out
            
            // Store the list of output files so that if we are killed 
            // they can be cleaned up
            PipelineStage.UNCLEAN_FILE_PATH.text += Utils.box(currentStage.context.output)?.join("\n") 
            
            c.setDelegate(currentStage.context)
	        log.info("Producing from inputs ${currentStage.context.@input}")
            def nextIn= body()
            if(nextIn)
                currentStage.context.nextInputs = nextIn
            else
                currentStage.context.nextInputs = null
        }
        return out
    }
    
    
    static Object noop(Closure c, Closure body) {
        def context = currentStage.context
        context.input = lastInputs
        context.output = lastInputs
        c.setDelegate(context)
        body()
        return context.output
    }
    
    /**
     * Specifies an output that keeps the same type of file but modifies 
     * it ("filters" it). Convenience wrapper for {@link #produce(Closure, Object, Closure)}
     * for the case where the same file extension is kept but a transformation
     * type is added to the name.
     */
    static Object filter(Closure c, String type, Closure body) {
        // TODO: use binding instead of lastInput
        // def rawInp = c.binding.variables.input
        def inp = Utils.first(lastInputs)
        log.info("Filter $type defined on inputs $inp")
        if(!inp) 
           throw new PipelineError("Expected input but no input provided") 
           
        produce(c, inp.replaceAll('(\\.[^\\.]*$)','.'+type+'$1'),body)
    }
     
    
    /**
     * Specifies an output that is a transformation of the input 
     * to a different format - keeps same file name but replaces
     * the extension.  Convenience wrapper for {@link #produce(Closure, Object, Closure)}
     * for the case where only the extension on the file is changed. 
     */
    static Object transform(Closure c, String extension, Closure body) {
        def inp = Utils.first(lastInputs)
        if(!inp) 
           throw new PipelineError("Expected input but no input provided") 
        produce(c, inp.replaceAll('\\.[^\\.]*$','.'+extension),body)
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
    
    static void msg(Closure,m) {
        def date = (new Date()).format("HH:mm:ss")
        println "$date MSG:  $m"
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
