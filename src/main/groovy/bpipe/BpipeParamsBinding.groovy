/*
 * Copyright (c) Murdoch Childrens Research Institute and Contributers
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 * 
 * Redistributions in binary form must reproduce the above copyright notice, this
 * list of conditions and the following disclaimer in the documentation and/or
 * other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */  
package bpipe

import groovy.transform.CompileStatic
import groovy.util.logging.Log

/**
 * Custom binding used to hold the CLI specified parameters.
 * <p>
 * The difference respect the default implementation is that
 * once the value is defined it cannot be overridden, so this make
 * the parameters definition works like constant values.
 * <p>
 * The main reason for that is to be able to provide optional default value
 * for script parameters in the pipeline script.
 *
 * Read more about 'binding variables'
 * http://groovy.codehaus.org/Scoping+and+the+Semantics+of+%22def%22
 *
 */
@Log
class BpipeParamsBinding extends Binding {
    
    List parameters = []
    
    /**
     * This global binding takes precendence of the binding assigned to the closures that
     * represent pipeline stages. This has the unfortunate effect that even if a pipeline stage
     * has a variable set explicitly with 'using' the global value still takes precedence. This
     * threadlocal is used to allow such variables to override the global value during execution.
     * 
     * @see PipelineStage#runBody
     * @see #withLocalVariables method
     */
    ThreadLocal<Map<String,Object>> stageLocalVariables = new ThreadLocal()
    
    
    /**
     * After Bpipe starts running, the global scope is set to read only
     */
    boolean readOnly = false

    def void setParam( String name, Object value ) {

        // mark this name as a parameter
        if( !parameters.contains(name) ) {
            parameters.add(name)
        }

        super.setVariable(name,value)
    }

    def void setVariable(String name, Object value) {

        // variable name marked as parameter cannot be overridden
        if( name in parameters ) {
            return
        }
        
        if(readOnly && this.variables.containsKey(name)) {
            
            Closure renderValue = { v -> v instanceof String ? "\"${v}\"" : v }
            String valueDef 
            if(value instanceof List) {
                valueDef =  value.collect { renderValue(it) }
            }
            else 
                valueDef = renderValue(value)
            
            throw new PipelineError(
                """

                    An attempt was made to assign to global variable $name after your pipeline already 
                    started running. To ensure thread safety, global variables may only be assigned 
                    before the pipeline starts. Solutions include:

                     - add 'def', 'var', or 'requires' before this assignment to define a local variable,
                       eg: def ${name} = $valueDef or: var ${name} : $valueDef
                     - define a branch variable using: branch.${name} = $valueDef

                    You can disable this behavior at your own risk by setting allowGlobalWrites=true in 
                    your bpipe.config file.
                """.stripIndent(15))
        }

        super.setVariable(name,value)
    }

    /**
     * Add as list of key-value pairs as binding parameters
     * <p>See {@link #setParam}
     *
     * @param listOfKeyValuePairs
     */
    def void addParams( List<String> listOfKeyValuePairs ) {

        if( !listOfKeyValuePairs ) return
		
		// Expand any file references into their key value equivalents
		def isFileReference = { it.startsWith("@") && !it.contains("=") }
		
        def parsePair = { pair ->
			
			if(isFileReference(pair)) return
			
            MapEntry entry = parseParam(pair)
            if( !entry ) {
                log.warning("The specified value is a valid parameter: '${pair}'. It must be in format 'key=value'")
            }
            else {
                if(entry.key == "region") {
                    setParam(entry.key, new RegionValue(entry.value))
                }
                else
                  setParam(entry.key, entry.value)
            }
        }
		
		def fileReferences = listOfKeyValuePairs.grep(isFileReference)
		fileReferences.each { fr ->
			fr = new File(fr.substring(1))
			if(!fr.exists())
				throw new PipelineError("The file $fr was  as a parameter file but could not be found. Please ensure this file exists.")
				
		    fr.eachLine { line -> 
				parsePair(line.trim()) 
		        log.info "Parsed parameter $line from file $fr"
			}
		}
		
		listOfKeyValuePairs.each(parsePair)
    }


    /**
     * Parse a key-value pair with the following syntax
     * <code>key = value </code>
     *
     * @param item The key value string
     * @return A {@link MapEntry} instance
     */
    static MapEntry parseParam( String item ) {
        if( !item ) return null

        def p = item.indexOf('=')
        def key
        def value
        if( p != -1 )  {
            key = item.substring(0,p)
            value = item.substring(p+1)

        }
        else {
            key = item
            value = null
        }

        if( !key ) {
            // the key is mandatory
            return null
        }

        if( value == null ) {
            value = true
        }
        else {
            if( value.isInteger() ) {
                value = value.toInteger()
            }
            else if( value.isLong() ) {
                value = value.toLong()
            }
            else if( value.isDouble() ) {
                value = value.toDouble()
            }
            else if( value.toLowerCase() in ['true','false'] ) {
                value = Boolean.parseBoolean(value.toLowerCase())
            }
        }

        new MapEntry( key, value )
    }
    
    @CompileStatic
    Object withLocalVariables(Map variables, Closure c) {
        this.stageLocalVariables.set(variables)
        try {
          return c()
        }
        finally {
            this.stageLocalVariables.set(null)
        }
    }
    
    Object getVariable(String name) {
        if(stageLocalVariables.get()?.containsKey(name)) {
            return this.stageLocalVariables.get()[name]
        }
        else {
            return super.getVariable(name)
        }
    }
}



