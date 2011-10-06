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
import java.util.Map;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import static Utils.*

/**
 * This context defines implicit functions and variables that are 
 * made available to Bpipe scripts.
 * <p>
 * Note: currently other functions are also made available by the 
 * PipelineCategory, however I hope to migrate these eventually
 * to all use this context.
 */
class PipelineContext {
    
    private static Logger log = Logger.getLogger("bpipe.PipelineContext");
    
    def defaultOutput
    
    def getDefaultOutput() {
        return this.@defaultOutput
    }
    
    def setDefaultOutput(defOut) {
        this.@defaultOutput = toOutputFolder(defOut)
    }
    
    def output
    
    def setOutput(o) {
        this.output = toOutputFolder(o)
    }
    
    def getOutput() {
        if(output == null) {
            return defaultOutput
        }
        return output
    }
    
    /**
     * Coerce all of the arguments (which may be an array of Strings or a single String) to
     * point to files in the local directory.
     */
    def toOutputFolder(outputs) {
        File outputFolder = new File(".")
        outputs = Utils.box(outputs).collect { new File(it).name }
        return Utils.unbox(outputs)
    }
    
    /**
     * Input to a stage - can be either a single value or a list of values
     */
    def input
    
    String getInput1() {
        return Utils.first(input)
    }
    
    String getInput2() {
        if(!Utils.isContainer(input) || input.size()<2)
            throw new PipelineError("Expected 2 or more inputs but 1 provided")
        return input[1]
     }
    
    String getInput3() {
        if(!Utils.isContainer(input) || input.size()<3)
            throw new PipelineError("Expected 3 or more inputs but 1 provided")
        return input[2]
    }
    
     /**
     * Check if there is an input, if so, return it.  If not, 
     * throw a helpful error message.
     * <br>
     * Note: if you wish to access the 'input' property without performing
     * this check (eg: to check if it is empty yourself) then you can use
     * direct property access - eg: ctx.@input
     * @return
     */
    def getInput() {
        if(this.@input == null || Utils.isContainer(this.@input) && this.@input.size() == 0) {
            throw new PipelineError("Input expected but not provided")
        }
        return this.@input
    }
    
    def setInput(def inp) {
        this.@input = inp
    }
    
    /**
     * Output stream, only opened if the stage body references
     * the "out" variable
     */
    FileOutputStream outFile
    
    /**
     * Iterate through the file line by line and pass each line to the given closure.
     * Output lines for which the closure returns true to the output.
     * If header is true, lines beginning with # will be passed to the 
     * body, otherwise they will be automatically output.
     */
    def filterLines(Closure c) {
        
        if(!input)
            throw new PipelineError("Attempt to grep on input but no input available")
            
        if(Runner.opts.t)
            throw new PipelineTestAbort("Would execute filterLines on input $input")
            
        new File(isContainer(input)?input[0]:input).eachLine {  line ->
            if(line.startsWith("#") || c(line))
                   getOut() << line << "\n"
        }
    } 
    
    /**
     * Search in the input file for lines matching speficied regex pattern
     * For each line found, if a function is passed, call it, otherwise
     * output the matching line to the default output file.
     * <p>
     * Lines starting with '#' are treated as comments or headers and are
     * not passed to the body.
     */
    def grep(String pattern, Closure c = null) {
        if(!input)
            throw new PipelineError("Attempt to grep on input but no input available")
            
        def regexp = Pattern.compile(pattern)
        new File(isContainer(input)?input[0]:input).eachLine {  
            if(it.startsWith("#"))
                return
                
            if(it =~ regexp) {
               if(c != null)
                   c(it) 
               else
                   getOut() << it << "\n"
            }
        } 
    }
    
    /**
     * Returns an output stream to which the current pipeline stage can write
     * directly to create output.  
     * 
     * @return
     */
    OutputStream getOut() {
        
        if(!outFile) {
          String fileName = Utils.first(output)
          if(Runner.opts.t)
	          throw new PipelineTestAbort("Would write to output file $fileName")
           
          outFile = new FileOutputStream(fileName)
        }
        return outFile
    }
}

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
     * List of past stages that have already produced outputs
     */
    static def stages = []
    
    static def joiners = []
    
    /**
     * Hack - unfortunately weird scope for closures makes the
     * map above not work when resolving embedded variable names
     */
    static lastOutput = null
    
    static nextInputs = null
    
    static lastInputs = null
    
    static Map closureNames = [:]
    
    static PipelineStage currentStage 
    
    static Object plus(Closure c, Closure other) {
        def result  = {  input1 ->
            
            currentStage = new PipelineStage(new PipelineContext(), c)
            stages << currentStage
            currentStage.context.setInput(input1)
            currentStage.run()
            Utils.checkFiles(currentStage.context.output)
                    
//          println "Output 1 is ${currentStage.context.output} next input is ${currentStage.nextInputs}"   
            
            // If the stage did not return any outputs then we assume
            // that the inputs to the next stage are the same as the inputs
            // to the previous stage
            def nextInputs = currentStage.nextInputs
            if(nextInputs == null)
                nextInputs = currentStage.context.@input
                
            Utils.checkFiles(nextInputs)
                
            lastInputs = nextInputs
            
            currentStage = new PipelineStage(new PipelineContext(), other)
            currentStage.context.@input = nextInputs
            stages << currentStage
            currentStage.run()
            return currentStage.nextInputs?:currentStage.context.output
        }
        joiners << result
        return result
    }
    
    /**
     * Specifies that the given output (out) will be produced
     * by the given closure, and skips execution of the closure
     * if the output is newer than all the current inputs.  
     */
    static Object produce(Closure c, Object out, Closure body) { 
        log.info "Producing $out from $currentStage.context"
        def lastInputs = currentStage.context.@input
        if(Utils.isNewer(out,lastInputs)) {
          msg(c,"Skipping steps to create $out because newer than $lastInputs ")
        }
        else {
            lastOutput = out 
            currentStage.context.output = out
            
            // Store the list of output files so that if we are killed 
            // they can be cleaned up
            PipelineStage.UNCLEAN_FILE_PATH.text += Utils.box(currentStage.context.output)?.join("\n")
            
            c.setDelegate(currentStage.context)
            def nextIn= body()
            if(nextIn)
                currentStage.nextInputs = nextIn
            else
                currentStage.nextInputs = null
        }
        return out
    }
    
    /**
     * Executes the given body with 'input' defined to be the 
     * 
     * @param c
     * @param inputs
     * @param body
     * @return
     */
    static Object from(Closure c, Object inputs, Closure body) {
        def reverseStages = stages.reverse()
        def orig = inputs
        
        // Add a final stage that represents the original inputs (bit of a hack)
        // You can think of it as the initial inputs being the output of some previou stage
        // that we know nothing about
        reverseStages << new PipelineStage(new PipelineContext(output:stages[0].context.input),null) 
        
        inputs = Utils.box(inputs).collect { String inp ->
            for(s in reverseStages) {
                if(inp.startsWith(".")) { // Find the most recent stage that output a matching file
                    def o = Utils.box(s.context.output)?.find { it?.endsWith(inp) } 
                    if(o)
                        return o
                }
            }
        }
        
        if(inputs.any { it == null})
            throw new PipelineError("Unable to locate one or more specified inputs matching spec $orig")
            
//        println "Found inputs $inputs for spec $orig"
        
        inputs = Utils.unbox(inputs)
        
        def oldInputs = currentStage.context.input 
        currentStage.context.input  = inputs
        
        def nextIn= body()
        if(nextIn)
            currentStage.nextInputs = nextIn
        else
            currentStage.nextInputs = null
        
        currentStage.context.input  = oldInputs
        return nextIn
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
        host.metaClass.properties.each { MetaBeanProperty p ->
            try {
                def x = p.getProperty(host)
                if(x instanceof Closure) {
                    closureNames[x] = p.name
                }
            }
            catch(Exception e) {
                // println "Ignoring $p ($e)"
            }
        }
    }
}
