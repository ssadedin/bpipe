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

import java.lang.annotation.Retention;

import java.lang.annotation.RetentionPolicy;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import bpipe.graph.Graph;
import static Utils.isContainer 
import static Utils.unbox 

/**
 * Annotation to indicate name of a pipeline stage.
 * Not used currently.
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface Name {
	String value();
}

class Config {
    static config = [ 
        columns: 100,
        
        // Default mode is "run", but "define" will just produce a definition
        // of the pipeline without executing it.  
        mode : "run"
    ]
}


/**
 * Main Pipeline class.  Used by client to start a pipeline
 * running by specifying initial inputs and setting up 
 * surrounding category to enable implicit pipeline stage functions.
 */
public class Pipeline {
    
    private static Logger log = Logger.getLogger("bpipe.Pipeline");
    
    /**
     * Global binding
     */
    static Binding externalBinding = new Binding()
    
    /**
     * Default run method - introspects all the inputs from the binding of the
     * pipeline closure.
     */
	static def run(Closure pipeline) {
       run(pipeline.binding.variables.args, pipeline.binding, pipeline) 
    }
    
	static def run(Object host, Closure pipeline) {
       run(pipeline.binding.variables.args, host, pipeline) 
    }
    
	static def run(def inputFile, Object host, Closure pipeline) {
        
        log.info("Running with input " + inputFile)
        
        // To make life easier when a single argument is passed in,
        // debox it from the array so that pipeline stages that 
        // expect a single input do not unexpectedly get an array    
        inputFile = Utils.unbox(inputFile)
        
        PipelineCategory.addStages(host)
        if(!(host instanceof Binding))
            PipelineCategory.addStages(pipeline.binding)

		// Create a command log file to capture all commands executed
        def mode = Config.config.mode 
        if(mode == "run")
	        executePipeline(inputFile, host, pipeline)
        else
        if(mode in ["diagram","diagrameditor"])
	        diagram(host, pipeline, Runner.opts.arguments()[0], mode == "diagrameditor")
	}

	private static executePipeline(def inputFile, Object host, Closure pipeline) {
        
        loadExternalStages()
        
        // We have to manually add all the external variables to the outer pipeline stage
        Pipeline.externalBinding.variables.each { 
            log.info "Loaded external reference: $it.key"
            pipeline.binding.variables.put(it.key,it.value) 
        }
        
		def cmdlog = new File('commandlog.txt')
		if(!cmdlog.exists())
			cmdlog << ""

		println("="*Config.config.columns)
		println("|" + (" Starting Pipeline at " + (new Date()).format("yyyy-MM-dd") + " ").center(Config.config.columns-2) + "|")
		println("="*Config.config.columns)

		initUncleanFilePath()
        
        boolean failed = false

		use(PipelineCategory) {
			// pipeline.setDelegate(host)
			PipelineCategory.currentStage = new PipelineStage(new PipelineContext(), pipeline())
			PipelineCategory.stages << PipelineCategory.currentStage
			PipelineCategory.currentStage.context.input = inputFile
			try {
				PipelineCategory.currentStage.run()
			}
			catch(PipelineError e) {
				System.err << "Pipeline failed!\n\n"+e.message << "\n\n"
                failed = true
			}
			catch(PipelineTestAbort e) {
				println "\n\nAbort due to Test Mode!\n\n  $e.message\n"
                failed = true
			}

			println("\n"+" Pipeline Finished ".center(Config.config.columns,"="))
			msg "Finished at " + (new Date())

            if(!failed) {
				def outputFile = Utils.first(PipelineCategory.currentStage.context.output)
				if(outputFile && !outputFile.startsWith("null") /* hack */ && new File(outputFile).exists()) {
					msg "Output is " + outputFile
				}
            }
		}

		// Make sure the command log ends with newline
		// as output is not terminated with one by default
		cmdlog << "\n"
	}
    
    /**
     * First delete and then initialize with blank contents the list of 
     * unclean files
     */
    static initUncleanFilePath() {
        if(!new File(".bpipe").exists())
            new File(".bpipe").mkdir()
            
        if(PipelineStage.UNCLEAN_FILE_PATH.exists()) {
            if(!PipelineStage.UNCLEAN_FILE_PATH.delete())
                throw new PipelineError("Unable to delete old unclean file cache in ${PipelineStage.UNCLEAN_FILE_PATH}")
                
        }
        PipelineStage.UNCLEAN_FILE_PATH.text = ""
    }
    
    static void loadExternalStages() {
        GroovyShell shell = new GroovyShell(externalBinding)
        def pipeFolder = new File(System.properties["user.home"], "bpipes")
        if(System.getenv("BPIPE_LIB")) {
            pipeFolder = new File(System.getenv("BPIPE_LIB"))
        }
        
        if(!pipeFolder.exists()) {
            log.warn("Pipeline folder $pipeFolder could not be found")
            return
        }
        
        def scripts = pipeFolder.listFiles().grep { it.name.endsWith("groovy") }.each { scriptFile ->
            log.info("Evaluating library file $scriptFile")
            try {
		        shell.evaluate(scriptFile)
            }
            catch(Exception ex) {
                log.error("Failed to evaluate script $scriptFile", ex)
                System.err.println("WARN: Error evaluating script $scriptFile: " + ex.getMessage())
            }
	        PipelineCategory.addStages(externalBinding)
        }
    }
    
    /**
     * This method is invoked from code generated by the @Transform annotation
     * 
     * @param stageName     name of stage that is declared to be a transformation
     * @param ext           new extension for file name produced by stage
     */
    static void declareTransform(String stageName, String ext) {
        println "Transform Declared:  $stageName maps to $ext"
        PipelineCategory.wrappers[stageName] = { body, input ->
            PipelineCategory.transform(ext) {
                body(input)
            }
        }
    }
    
    static Closure declarePipelineStage(String stageName, Closure c) {
        // println "Pipeline stage declared $stageName"
        PipelineCategory.closureNames[c] = stageName
        if(c.properties.containsKey("binding")) {
            c.binding.variables[stageName] = c
        }
        return c
    }
    
    /**
     * This method creates a diagram of the pipeline instead of running it
     */
	static def diagram(Object host, Closure pipeline, String fileName, boolean editor) {
        
        // Figures out what the pipeline stages are 
        if(host)
			pipeline.setDelegate(host)
            
        use(DefinePipelineCategory) {
            pipeline()()
        }
        
        // Now make a graph
        // println "Found stages " + DefinePipelineCategory.stages
        Graph g = new Graph(DefinePipelineCategory.stages)
        if(editor) {
            g.display()
        }
        else {
	        String outputFileName = fileName+".png"
	        println "Creating diagram $outputFileName"
	        g.render(outputFileName)
        }
    }
}
