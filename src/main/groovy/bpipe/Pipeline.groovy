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
package bpipe

import groovy.text.GStringTemplateEngine;
import groovy.text.GStringTemplateEngine.GStringTemplate;

import java.lang.annotation.Retention;

import java.lang.annotation.RetentionPolicy;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import bpipe.graph.Graph;
import static Utils.isContainer 
import static Utils.unbox 

/**
 * Main Pipeline class.  Used by client to start a pipeline
 * running by specifying initial inputs and setting up 
 * surrounding category to enable implicit pipeline stage functions.
 */
public class Pipeline {
    
    private static Logger log = Logger.getLogger("bpipe.Pipeline");
    
    /**
     * The thread id of the master thread that is running the baseline root
     * pipeline
     */
    static Long rootThreadId
    
    /**
     * Global binding - variables and functions (including pipeline stages)
     * that are available to all pipeline stages.  Don't put
     * variables that are specific to a pipeline in here (even though this
     * is an instance method) because they are injected in a way that makes
     * them appear across all pipeline instances.  For pipeline instance 
     * specific variables, see {@link #variables}
     */
    Binding externalBinding = new Binding()
    
    /**
     * Variables that are only available to this specific instance of a 
     * running pipeline.
     */
    Map<String,String> variables = [:]
    
    /**
     * List of past stages that have already produced outputs.  This 
     * list is built up progressively as pipeline stages execute.
     */
    def stages = []
    
    /**
     * A list of "dummy" stages that are actually used to link other stages together
     */
    def joiners = []
	
	/**
	 * If this pipeline was spawned as a child of another pipeline, then
	 * the parent is set to that pipeline
	 */
	Pipeline parent = null
    
    /**
     * A name for the pipeline that is added to output file names when 
     * the pipeline is run as a child pipeline.  This is null and not used
     * in the default, root pipeline
     */
    String name 
    
    /**
     * Whether the name for the pipeline has been applied in the naming of
     * files that are produced by the pipeline.  A pipeline segment with a 
     * name should only inject its name into the output file name sequence
     * once, so this flag is set true when that happens.
     */
    boolean nameApplied = false
    
    /**
     * Flag that is set to true if this pipeline has executed and experienced 
     * a failure
     */
    boolean failed = false
    
    /**
     * The base context from which all others are generated
     */
    PipelineContext rootContext
    
    /**
     * Due to certain constraints in how Groovy handles operator 
     * overloading and injections of methods into classes at runtime,
     * we can only add certain methods in a static context.  To handle that,
     * we have a global (non-thread-safe) "current" pipeline.
     * <p>
     * To access the pipeline, use the #withCurrentUnderConstructionPipeline method
     * which will synchronize on the variable so that we can guarantee that no two
     * are accessed at the same time.
     */
	static Pipeline currentUnderConstructionPipeline = null
    
    /**
     * In a situation where multiple parallel exeuction paths are
     * running there are child pipelines. The child stages
     * inside the child pipeline are associated with the pipeline 
     * by this thread local variable
     */
    static ThreadLocal<Pipeline> currentRuntimePipeline = new ThreadLocal()
	
	
	/**
	 * Documentation about this pipeline
	 */
	static Map<String,Object> documentation = [:]
    
    /**
     * Create a pipeline, initialized with all the already discovered
     * segment-joining closures
     */
    Pipeline() {
        this.joiners = segmentJoiners.clone()
    }
	
	/**
	 * Allow user to add arbitrary documentation about their pipeline
	 */
	static about(Map<String,Object> docs) {
		documentation += docs
	}
    
    /**
     * Due to certain constraints in how Groovy handles operator 
     * overloading and injections of methods into classes at runtime,
     * we can only add certain methods in a static context.  To handle that,
     * we have a global (non-thread-safe) "current" pipeline.  Fortunately this 
     * is only needed during the pipeline definition stage and not during 
     * execution, so in fact we can afford to be single threaded in how
     * we access this pipeline and only one pipeline is ever under construction
     * at a time (even if multiple pipelines are constructed).
     * 
     * @param c
     * @return
     */
	static Pipeline withCurrentUnderConstructionPipeline(Pipeline p, Closure c) {
        synchronized(Pipeline.class) {
            currentUnderConstructionPipeline = p
    	    c(currentUnderConstructionPipeline)	
            currentUnderConstructionPipeline = null
        }
	}
	
	static def chr(Object... objs) {
		Set<Chr> result = [] as Set
		for(Object o in objs) {
			
			if(o instanceof Closure) 
				o = o()
			
			if(o instanceof Range) {
				for(r in o) {
					result << new Chr('chr'+r)
				}
			}
			else 
			if(o instanceof String || o instanceof Integer) {
				result << new Chr('chr'+o)
			}
		}
		
		return result
	}
    
    /**
     * Define a pipeline segment - allows a pipeline segment to
     * be created and stored in a variable
     */
	static def segment(Closure pipelineBuilder) {
        
		Pipeline pipeline = new Pipeline()
        PipelineCategory.addStages(pipelineBuilder.binding)
		if(!pipelineBuilder.binding.variables.containsKey("BPIPE_NO_EXTERNAL_STAGES"))
	        pipeline.loadExternalStages()

        Object result = pipeline.execute([], pipelineBuilder.binding, pipelineBuilder, false)
        segmentJoiners.addAll(pipeline.joiners)
        return result
    }
    
    static List<Closure> segmentJoiners = []
	
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
    
	static def run(def inputFile, Object host, Closure pipelineBuilder) {
        
        log.info("Running with input " + inputFile)
        
		Pipeline pipeline = new Pipeline()
        
        // To make life easier when a single argument is passed in,
        // debox it from the array so that pipeline stages that 
        // expect a single input do not unexpectedly get an array    
        inputFile = Utils.unbox(inputFile)
        
        PipelineCategory.addStages(host)
        if(!(host instanceof Binding))
            PipelineCategory.addStages(pipelineBuilder.binding)
            
        pipeline.loadExternalStages()
        pipeline.joiners += segmentJoiners

        def mode = Config.config.mode 
        if(mode == "run" || mode == "documentation") // todo: documentation should be its own mode! but can't support that right now
	        pipeline.execute(inputFile, host, pipelineBuilder)
        else
        if(mode in ["diagram","diagrameditor"])
	        pipeline.diagram(host, pipelineBuilder, Runner.opts.arguments()[0], mode == "diagrameditor")
        else
        if(mode in ["documentation"])
	        pipeline.documentation(host, pipelineBuilder, Runner.opts.arguments()[0])
            
	}
    
    /**
     * Runs the specified closure in the context of this pipeline 
     * and both decrements and notifies the given counter when finished.
     */
    void runSegment(def inputs, Closure s, AtomicInteger counter) {
        try {
            this.rootContext = createContext()
            
            if(currentRuntimePipeline.get() == null) 
                currentRuntimePipeline.set(this)
		
            def currentStage = new PipelineStage(rootContext, s)
            log.info "Running segment with inputs $inputs"
            this.addStage(currentStage)
            currentStage.context.@input = inputs
            try {
                currentStage.run()
            }
            catch(PipelineError e) {
                log.info "Pipeline segment failed (2): " + e.message
                System.err << "Pipeline failed!\n\n"+e.message << "\n\n"
                failed = true
            }
            catch(PipelineTestAbort e) {
                log.info "Pipeline segment aborted due to test mode"
                println "\n\nAbort due to Test Mode!\n\n" + Utils.indent(e.message) + "\n"
                failed = true
            }
        }
        finally {
            if(counter != null) {
	            int value = counter.decrementAndGet()
	            log.info "Finished running segment for inputs $inputs and decremented counter to $value"
	            synchronized(counter) {
	                counter.notifyAll()
	            }
	        }
        }
    }

	private Closure execute(def inputFile, Object host, Closure pipeline, boolean launch=true) {
        
        Pipeline.rootThreadId = Thread.currentThread().id
        
        // We have to manually add all the external variables to the outer pipeline stage
        this.externalBinding.variables.each { 
            log.info "Loaded external reference: $it.key"
            if(!pipeline.binding.variables.containsKey(it.key))
	            pipeline.binding.variables.put(it.key,it.value) 
            else
                log.info "External reference $it.key is overridden by local reference"    
        }
        
        // Add all the pipeline variables to the external binding
        this.externalBinding.variables += pipeline.binding.variables
        
        def cmdlog = CommandLog.log
        if(launch) {
            cmdlog.write("")
            String startDateTime = (new Date()).format("yyyy-MM-dd") + " "
            cmdlog << "#"*Config.config.columns 
            cmdlog << "# Starting pipeline at " + (new Date())
            cmdlog << "# Input files:  $inputFile"
    		println("="*Config.config.columns)
    		println("|" + " Starting Pipeline at $startDateTime".center(Config.config.columns-2) + "|")
    		println("="*Config.config.columns)
			
			about(startedAt: new Date())
        }
    
		def constructedPipeline
		use(PipelineCategory) {
            
            // Build the actual pipeline
            Pipeline.withCurrentUnderConstructionPipeline(this) {
				constructedPipeline = pipeline()
			}
			
            if(launch) {
                runSegment(inputFile, constructedPipeline, null)
    			println("\n"+" Pipeline Finished ".center(Config.config.columns,"="))
    			rootContext.msg "Finished at " + (new Date())
				about(finishedAt: new Date())
				
				EventManager.instance.signal(PipelineEvent.FINISHED, failed?"Failed":"Succeeded")
                if(!failed) {
					summarizeOutputs(stages)
                }
    		}
		}

		// Make sure the command log ends with newline
		// as output is not terminated with one by default
		cmdlog << ""
        
        if(launch && (Config.config.mode == "documentation" || Config.config.report)) {
            documentation()
        }
        
        return constructedPipeline
	}
	
    PipelineContext createContext() {
       def ctx = new PipelineContext(this.externalBinding, this.stages, this.joiners) 
       ctx.outputDirectory = Config.config.defaultOutputDirectory
       return ctx
    }
    
    /**
     * Create a new pipeline that is initialized with copies of all the same state
     * that this pipeline has.  The new pipeline can execute concurrently with this
     * one without interfering with this one's state.
     * <p>
     * Note:  the new pipeline is not run by this method; instead you have to
     *        call one of the {@link #run(Closure)} methods on the returned pipeline
     */
    Pipeline fork() {
        Pipeline p = new Pipeline()
        p.stages = [] + this.stages
        p.joiners = [] + this.joiners
		p.parent = this
        return p
    }
    
    private void loadExternalStages() {
        GroovyShell shell = new GroovyShell(externalBinding)
        def pipeFolder = new File(System.properties["user.home"], "bpipes")
        if(System.getenv("BPIPE_LIB")) {
            pipeFolder = new File(System.getenv("BPIPE_LIB"))
        }
        
        if(!pipeFolder.exists()) {
            log.warning("Pipeline folder $pipeFolder could not be found")
            return
        }
        
        def scripts = (pipeFolder.listFiles().grep { it.name.endsWith("groovy") } + loadedPaths).each { scriptFile ->
            log.info("Evaluating library file $scriptFile")
            try {
		        shell.evaluate("import static Bpipe.*; binding.variables['BPIPE_NO_EXTERNAL_STAGES']=true; " + scriptFile.text)
            }
            catch(Exception ex) {
                log.severe("Failed to evaluate script $scriptFile: "+ ex)
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
     * Paths that have been added to the script using the <code>load</code>
     * Bpipe command.
     */
    static List<File> loadedPaths = []
    
    /**
     * Include pipeline stages from the specified path into the pipeline
     * @param path
     */
    static synchronized void load(String path) {
        File f = new File(path)
        if(!f.exists())
            throw new PipelineError("A file requested to be loaded from path $path but this path could not be accessed.")
            
        loadedPaths << f
    }
    
    /**
     * This method creates documentation for a pipeline based on the 
     * pipeline stage names and any configured documentation that is added to them
     */
	def documentation() {
        
        // Now make a graph
        File docDir = new File("doc")
        if(!docDir.exists()) {
            docDir.mkdir()
        }
		
		// We build up a list of pipeline stages
		// so the seed for that is a list with one empty list
		def docStages = [ [] ]
		 
		fillDocStages(docStages)
		
		if(!documentation.title)
			documentation.title = "Pipeline Report"
	        
         Map docBinding = [
            stages: docStages,
			pipeline: this
        ]
		 
		if(docStages.any { it.stageName == null })
			throw new IllegalStateException("Should NEVER have a null stage name here")
        
        // Use HTML templates to generate documentation
        InputStream templateStream
        File srcTemplateDir = new File(System.getProperty("bpipe.home") + "/src/main/html/bpipe")
        if(srcTemplateDir.exists())
            templateStream = new FileInputStream(new File(srcTemplateDir, "index.html"))
        else
            templateStream = new FileInputStream(new File(System.getProperty("bpipe.home") + "/html", "index.html"))
            
        GStringTemplateEngine e = new GStringTemplateEngine()
        templateStream.withReader { r ->
            def template = e.createTemplate(r).make(docBinding)
            new File(docDir,"index.html").text = template.toString()
        }
        templateStream.close()
        
        println "Generated documentation in $docDir"
    }
	
	void fillDocStages(List pipelines) {
		
		log.fine "Filling stages $stages"
		
		for(List docStages in pipelines.clone()) {
			
			for(PipelineStage s in stages) {
					
				// No documentation for anonymous joiner stages
				if(s.body in joiners)
					continue
					
				if(!s.children && s?.context?.stageName != null) {
					log.fine "adding stage $s.context.stageName from pipeline $this"
					pipelines.each { it << s }
				}
					
				if(s in docStages || (parent != null && s in parent.stages))
					continue
					
				// if it has children, generate them
				if(s.children) {
				
					// Take out the original
					pipelines.remove(docStages)
					
					// Replace it for an entry with each child pipeline
					pipelines.addAll s.children.collect { Pipeline childPipeline ->
						List childStages = [ [] ]
						childPipeline.fillDocStages(childStages)
						return childStages
					}.collect { it[0] }
				}
			}
		}
		
		log.fine "Result: $pipelines"
	}
    
    /**
     * This method creates a diagram of the pipeline instead of running it
     */
	def diagram(Object host, Closure pipeline, String fileName, boolean editor) {
        
        // We have to manually add all the external variables to the outer pipeline stage
        this.externalBinding.variables.each {
            log.info "Loaded external reference: $it.key"
            if(!pipeline.binding.variables.containsKey(it.key))
                pipeline.binding.variables.put(it.key,it.value)
            else
                log.info "External reference $it.key is overridden by local reference"
        }
        
        // Figures out what the pipeline stages are 
        if(host)
			pipeline.setDelegate(host)
            
        use(DefinePipelineCategory) {
            pipeline()()
        }
        
        // Now make a graph
        // println "Found stages " + DefinePipelineCategory.stages
        Graph g = new Graph(DefinePipelineCategory.inputStage)
        if(editor) {
            g.display()
        }
        else {
	        String outputFileName = fileName+".png"
	        println "Creating diagram $outputFileName"
	        g.render(outputFileName)
        }
    }
    
    
    void addStage(PipelineStage stage) {
        synchronized(this.stages) {
            this.stages << stage
        }
    }
	
	void summarizeOutputs(List stages) {
		def all = Utils.box(stages[-1].context.output).grep { it && !it.startsWith("null") && new File(it.toString()).exists() }
		if(all.size() == 1) {
			rootContext.msg "Output is " + all[0]
		}
		else
		if(all) {
			if(all.size() < 5) {
				rootContext.msg "Outputs are: \n\t" +  all.join("\n\t")
			}
		}
		
		
	}
}
