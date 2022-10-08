package bpipe

import groovy.transform.CompileStatic

/**
 * Tracks the output files that have been referenced in this pipeline run
 * 
 * @author simon.sadedin
 */
@Singleton
class OutputFileRegistry {
    
    static class FileRegistration {
        String stageName
        
        PipelineFile file
    }
    
    
    /**
     * The internal set that tracks all the output files from this pipeline run
     */
    HashMap<String, FileRegistration> outputFiles = Collections.synchronizedMap(new HashMap<String, FileRegistration>())
    
    /**
     * Register an output, checking that it has not already been registered.
     * 
     * @param context
     * @param toRegister
     */
    @CompileStatic
    void register(final PipelineContext context, final List<PipelineFile> toRegister, boolean failIfDuplicates) {
        if(toRegister == null || toRegister.isEmpty())
            return
            
        List<PipelineFile> alreadyRegistered = toRegister.findAll { it.path in outputFiles }
        if(alreadyRegistered && failIfDuplicates) {
            throw new PipelineError(
                """
                    Pipeline stage $context.stageName attempted to create one or more files that were already created in another pipeline stage:
                """.stripIndent() + alreadyRegistered.collect { it.path + " : already created in " + outputFiles[it.path].stageName  }.join('\n'),
                context
            )
        }
        
        for(PipelineFile file in toRegister) { 
            outputFiles[file.path] = new FileRegistration(stageName: context.stageName, file: file)
        }
    }
    
    @CompileStatic
    static OutputFileRegistry getTheInstance() {
        return OutputFileRegistry.instance
    }
    
}
