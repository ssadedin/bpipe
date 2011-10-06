package bpipe

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.logging.Logger;
import java.util.regex.Pattern;
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
    static config = [ columns: 100 ]
}


/**
 * Main Pipeline class.  Used by client to start a pipeline
 * running by specifying initial inputs and setting up 
 * surrounding category to enable implicit pipeline stage functions.
 */
public class Pipeline {
    
    private static Logger log = Logger.getLogger("bpipe.Pipeline");
    
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
        
        Class clazz = host.class
		
		// Create a command log file to capture all commands executed
        def cmdlog = new File('commandlog.txt')
		if(!cmdlog.exists())
			cmdlog << ""
        
        // To make life easier when a single argument is passed in,
        // debox it from the array so that pipeline stages that 
        // expect a single input do not unexpectedly get an array    
        inputFile = Utils.unbox(inputFile)
            
        PipelineCategory.addStages(host)
        if(!(host instanceof Binding))
	        PipelineCategory.addStages(pipeline.binding)
            
        println("="*Config.config.columns)
	    println("|" + (" Starting Pipeline at " + (new Date()).format("yyyy-MM-dd") + " ").center(Config.config.columns-2) + "|")
        println("="*Config.config.columns)
        
        initUncleanFilePath()

		use(PipelineCategory) {
          pipeline.setDelegate(host)
	      PipelineCategory.currentStage = new PipelineStage(new PipelineContext(), pipeline())
	      PipelineCategory.stages << PipelineCategory.currentStage
	      PipelineCategory.currentStage.context.input = inputFile
		  try {
              PipelineCategory.currentStage.run()
		  }
		  catch(PipelineError e) {
			  System.err << "Pipeline failed!\n\n"+e.message << "\n\n"
		  }
		  catch(PipelineTestAbort e) {
			  println "\n\nAbort due to Test Mode!\n\n  $e.message\n"
		  }
          
          println("\n"+" Pipeline Finished ".center(Config.config.columns,"="))
          msg "Finished at " + (new Date())
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
}

/**
 * A simple alias for Pipeline
 * 
 * @author simon
 */
class Bpipe extends Pipeline {
    
}
