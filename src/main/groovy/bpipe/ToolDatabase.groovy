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

import groovy.util.logging.Log;

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
    void init(ConfigObject parentConfig, Binding binding=null) {
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
}
