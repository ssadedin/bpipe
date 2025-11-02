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

import bpipe.ExecutedProcess
import bpipe.Runner
import bpipe.Utils
import groovy.transform.CompileStatic
import groovy.util.logging.Log
import java.util.regex.Matcher
import java.util.regex.Pattern

@Log
class RunPipelineCommand extends BpipeCommand {
    
    ExecutedProcess result
    
    File runDirectory 
    
    Closure onDirectoryConfigured
    
    public RunPipelineCommand(List<String> args) {
        super("run", args);
    }
    
    @Override
    public void run(Writer out) {
        
        if(dir == null) 
            throw new IllegalArgumentException("Directory parameter not set. Directory for command to run in must be specified")
        
        File dirFile = new File(dir).absoluteFile
        
        dirFile = createRunDirectory(dirFile)
        
        log.info "Updated run directory is $dirFile"
        
        if(!dirFile.exists())
            throw new IllegalArgumentException("Unable to create directory requested for pipeline run $dir.")
            
        if(this.onDirectoryConfigured!=null) {
            this.onDirectoryConfigured(dirFile)
        }
        
        log.info "Running with arguments: " + args + " in directory " + dirFile;
        
        this.runDirectory = dirFile
        
        List<String> cmd = [ bpipeHome + "/bin/bpipe", "run" ] 
        cmd.addAll(args)

        // Just for clarity, a reference to refer to inside the closure below
        final Map<String,String> commandEnvironment = this.environment

        result = Utils.executeCommand(cmd, out:out, err: out) {
            directory(dirFile)

            Map env = environment()
            
            env.put('BPIPE_QUIET','true')
            env.putAll(commandEnvironment)
        }
        
    }

    /**
     * This lock prevents multiple concurrent jobs launched by the bpipe
     * agent from attempting to create the same directory when the directories
     * are templated.
     */
    private final static Object RUN_DIRECTORY_COMPUTE_LOCK = new Object()
    
    /**
     * Iterate the path hierarchy and create any directories that do not exist, 
     * examining each level for possible incrementing templates, where such a template
     * is specified with <code>{inc}</code> in the body of a path.
     */
    @CompileStatic
    File createRunDirectory(File dirFile) {
        
        synchronized(RUN_DIRECTORY_COMPUTE_LOCK) { 
                
            List<String> dirParts = dirFile.path.tokenize('/')
            String parentPath = '/'
            String resultPath = '/' + dirParts.collect { String part ->
                String newPart = computeRunDirectoryPart(new File(parentPath), part)
                parentPath = parentPath + '/' + newPart
                new File(parentPath).mkdirs()
                return newPart
            }.join('/')
            
            File resultFile = new File(resultPath)
            if(!resultFile.exists()) {
                log.info "Creating directory path ${resultFile} to run command" 
                resultFile.mkdirs()
            }
            return resultFile
        }
    }
    
    /**
     * Regular expression to identify increment within paths
     */
    public static final Pattern PATH_INCREMENTER_REGEX = ~/.*(\{inc\}).*/
   
    /**
     * Attempt to resolve the pipeline file from the arguments provided
     * 
     * @return
     */
    @SuppressWarnings("deprecation")
    String getPipelineFile() {
        CliBuilder cli = new CliBuilder()
        Runner.configureRunCli(cli)
        OptionAccessor opts = cli.parse(args)
        return opts.arguments()[0]
    }
     
    /**
     * Create the given childDir, replacing any incrementing portion
     * of the path (specified by the form <code>{inc}</code> within the path.
     * 
     * @return  the created path
     */
    String computeRunDirectoryPart(final File parentFile, final String childDir) {
        
        assert parentFile.exists()
        assert parentFile.isDirectory()
            
        Matcher m = PATH_INCREMENTER_REGEX.matcher(childDir)
        if(!m) 
            return childDir
                
        List<Integer> matched_indices = findChildDirectoryIndices(parentFile,childDir)
                          
        log.info "Found ${matched_indices.size()} matching directories in dir ${parentFile}"
            
        int nextValue = (matched_indices.max()?:0) + 1
            
        return childDir.replaceFirst(/\{inc\}/, String.format('%04d', nextValue))
    }

    /**
     * Identify directories within the given parent dir that match the
     * form of the template given where {inc} is treated as a numeric
     * wildcard.
     * 
     * @param parentFile
     * @param dirPart
     * @return
     */
    List<Integer> findChildDirectoryIndices(File parentFile, String template) {
        
        Pattern incPattern = Pattern.compile(template.replaceFirst(/\{inc\}/, '([0-9]{1,6})'))

        // Find all the dirs within the parent path that match the given expression
        List<Integer> matching_dirs =
              parentFile.listFiles()
                        .grep { File f -> f.isDirectory() }
                        .collect { incPattern.matcher(it.name) }
                        .grep { it.matches() }
                        .collect { it[0][1] } // first group from regex match match
                        .grep { it.isInteger() }
                        .collect { it.toInteger() }
        return matching_dirs
    }
}

