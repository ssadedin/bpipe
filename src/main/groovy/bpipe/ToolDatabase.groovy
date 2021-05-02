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

import groovy.transform.CompileStatic
import groovy.util.logging.Log;
import java.util.regex.Pattern

/**
 * Database of tools, indexed by tool name.  Tracks the version of each tool
 * that we can figure out by probing
 * 
 * @author ssadedin
 */
@Singleton
@Log
class ToolDatabase {
    
    ConfigObject config
    
    Map<String,Tool> tools = [:]
    
    Binding binding
    

    /**
     * Initializes the tool database with the given configuration
     * 
     * @param parentConfig
     */
    void init(Map parentConfig, Binding binding=null) {
        if(parentConfig.containsKey("tools")) {
            log.info "Loading tool database from user configuration"
            config = parentConfig.get("tools")
            config.each { key, value ->
                tools[key] = new Tool(key, value)
            }
        }
    }
    
    /**
     * Probe the given command to see if it contains any tools that should be
     * added to documentation.
     */
    Map<String,Tool> probe(String command) {
        def result = [:]
        tools.each { String name, Tool tool ->
            if(!commandContainsTool(command,name))
                return
            
            try {
                tool.probe(command) 
            }
            catch(Exception e) {
                log.severe("Probe for tool $name failed: " + e.toString())
            }
            
            result[name] = tool
        }
        
        return result?:null
    }
    
    /**
     * Return true if the given command contains a reference to a program
     * with the specified name, in a plausible context such that it might
     * be executed.
     * 
     * @param command
     * @param name
     * @return
     */
    boolean commandContainsTool(String command, String name) {
        return Tool.indexOfTool(command,name) > -1
    }
    
    void hd(msg) {
        println("\n"+(" " + msg + " ").center(Tool.PRINT_WIDTH,"=") + "\n")
    }
 
    void installTools(ConfigObject installCfg) {
        
        List errors = []
        for(toolName in installCfg.keySet()) {
            
            Tool tool = tools[toolName]
            if(!tool) {
                String error = "ERROR: Tool $toolName is not in the tool database."
                System.err.println error
                errors << error
                continue
            }
            List toolErrors = tool.install()
            if(toolErrors)
                errors.addAll(toolErrors)
        }
            
        hd "Finished"
            
        if(!errors) {
            println "Success - everything checks out!"
        }
        else {
            println "Some problems occurred: \n"
                
            errors.each {
                println "* " + it
            }
                
            println ""
            println "Please resolve these before trying to run!"
        }
    }
    
    static Pattern GROOVY_ALL_PATTERN = null
    
    /**
     * Set automatic variables pointing to tool dependencies, for tools defined in the 'install' or
     * 'dependencies' section of the configuration (these are aliases for each other).
     */
    public void setToolVariables(Binding externalBinding) {
        
        setGroovyVariables(externalBinding)
        
        if(!Config.userConfig.containsKey('install') && !Config.userConfig.containsKey('dependencies'))
            return
            
        ConfigObject installCfg = Config.userConfig.install.merge(Config.userConfig.dependencies)
        ConfigObject toolsCfg = installCfg.tools
        
        String scriptPath = new File(Config.config.script).absoluteFile.parentFile.absolutePath
        String basePath = ('base' in installCfg) ? installCfg.base : scriptPath
        File toolBaseDir = new File(basePath)
        for(String toolName in toolsCfg.keySet()) {
            String toolVariable = toolName.toUpperCase()
            if(externalBinding.variables.containsKey(toolVariable)) {
                log.info "Skip setting tool variable $toolVariable because already defined"
                continue
            }
                    
            Tool tool = ToolDatabase.instance.tools[toolName]
            if(!tool) {
                throw new PipelineError("A tool $toolName was referenced in the install section but is not a known tool in the tool database. Please define this tool in your 'tools' section.")
            }
                
            if(tool.config.containsKey("installExe") && tool.config.containsKey("installPath")) {
                File exeFile = new File(toolBaseDir, tool.config.installPath + "/" + tool.config.installExe)
                log.info "Setting tool variable $toolVariable automatically to $exeFile.absolutePath based on install section of config"
                externalBinding.variables.put(toolVariable,exeFile.absolutePath)
            }
        }
    }
    
    /**
     * To make using groovy based tools in pipelines easier, there is some special logic
     * to help within locating the groovy-all jar.
     */
    private void setGroovyVariables(Binding externalBinding) {
        if(!Config.userConfig?.groovy?.containsKey('executable'))
            return 
            
        String rawExe = Config.userConfig.groovy.executable
        log.info "Groovy executable is configured as ${rawExe}"
        
        File groovyBin = new File(rawExe)
            
        if(!groovyBin.exists()) {
            groovyBin = new File(Config.scriptDirectory, groovyBin.path).canonicalFile
            log.info "Configured groovy executable located relative to pipeline directory at $groovyBin"
        }
            
        if(groovyBin.exists()) {
                
            if(GROOVY_ALL_PATTERN == null) {
                GROOVY_ALL_PATTERN = ~/groovy-all-[0-9].[0-9].[0-9].jar/
            }
                
            externalBinding.variables.put('GROOVY',groovyBin.absolutePath)
                
            // Find groovy-all
            File groovyInstallDir = groovyBin.absoluteFile.parentFile.parentFile
            externalBinding.variables.put('GROOVY_HOME',groovyInstallDir.absolutePath)
                
            log.info "Found groovy specified as executable: setting up GROOVY_ALL_JAR variable based on groovy dir: $groovyInstallDir"
                
            File groovyAll = new File(groovyInstallDir,'embeddable').listFiles().find { it.name.matches(GROOVY_ALL_PATTERN) }
            if(groovyAll != null) {
                log.info "GROOVY_ALL_JAR set to $groovyAll"
                externalBinding.variables.put('GROOVY_ALL_JAR',groovyAll)
            }
            else {
                log.info "No groovy-all jar could be located relative to groovy binary $groovyBin"
            }
        }
        else {
            String msg = 'Groovy executable was configured but the configured location could not be resovled: ' + groovyBin + ' or ' + rawExe
            log.warning(msg)
            System.err.println(msg)
        }
    }
    
    @CompileStatic
    static ToolDatabase getTheInstance() {
        return ToolDatabase.instance
    }
}
