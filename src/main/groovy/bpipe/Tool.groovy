package bpipe

import java.io.Reader
import java.util.List
import java.util.Map

import groovy.lang.Closure
import groovy.util.ConfigObject
import groovy.util.logging.Log

/**
 * Data structure to track information about each tool in the tool database
 * @author ssadedin
 */
@Log  
class Tool {
    
    Tool(String name, ConfigObject obj) {
        this.config = obj
        this.probeCommand = obj.probe
        this.name = name
        log.info "Configured meta data about tool $name" 
        
        meta += obj
    }
    
    /**
     * Name of this tool as mentioned in the config file
     */
    String name
    
    /**
     * The module to which this tool belongs. Multiple tools can belong to
     * the same module. Generally, they would have the same version in that case
     */
    String module
    
    /**
     * Version of the tool as determined by probe command.
     * Null until the tool has actually been probed (so only 
     * valid if probed and probeSucceeded are true)
     */
    String version = null
    
    /**
     * Executable command that returns the version of the tool
     */
    String probeCommand
    
    /**
     * Set to true after the tool has been probed for version information
     */
    boolean probed = false
    
    /**
     * Set to true if the probe was executed and succeeded
     */
    boolean probeSucceeded =false
    
    /**
     * Miscellaneous open ended meta data about the tool
     */
    Map<String,Object> meta = [:]
    
    /**
     * The full configuration data for the tool
     */
    ConfigObject config
    
    /**
     * Standard input - used in install process if it is necessary to ask user questions
     */
    Reader stdin = null
    
    /**
     * Executes the probe command to determine the version of the given tool
     * 
     * @param hostCommand
     */
    String probe(String hostCommand) {
        
        OSResourceThrottle.instance.withLock {
            
            if(probed) 
                return version
            
            String binary = expandToolName(hostCommand)
            log.info "Binary for $name expanded to $binary"
            
            String installExe = ("installExe" in config) ? config.installExe : ""
            String realizedCommand 
            if(installExe.endsWith("Rscript")) {
                String rscriptExe = Utils.resolveRscriptExe()
                realizedCommand = /set -o pipefail; echo "cat(as.character(packageVersion('$name')))" | $rscriptExe - 2>&1 | head -n 1 | grep -v Error/
            }
            else
                realizedCommand = probeCommand.replaceAll("%bin%", binary)
            
            log.info "Probing version of tool using probe command $realizedCommand"
            
            File dir = new File(Config.config.script).absoluteFile.parentFile
            ProcessBuilder pb = new ProcessBuilder(['bash','-c',realizedCommand] as String[]).directory(dir)
            Process process = pb.start()
            
            StringWriter output = new StringWriter()
            StringWriter errorOutput = new StringWriter()
            process.consumeProcessOutput(output, errorOutput)
            int exitCode = process.waitFor()
            if(exitCode == 0) {
                version = output.toString().trim()
                probeSucceeded = true
            }
            else
            if(installExe != "Rscript") {
                version = "Unable to determine version (error occured, see log)" 
                log.info "Probe command $realizedCommand failed with error output: " + errorOutput.toString()
            }
            probed = true
        }
        return version
    }
    
    static String NON_PATH_TOKEN = " \t;"
    
    /**
     * Find the tool name in the host command and then 
     * expand it to include all tokens that are legitimate 
     * parts of a path name, thus for tools represented by an 
     * absolute path, returning the full absolute path, and for
     * those implicitly in the default path already, just returning
     * the raw tool name. 
     * <p>
     * In addition to this behavior, a special exception is made for JAR
     * files which expands them to include the ".jar" extension so that 
     * the entry in bpipe.config can be without ".jar" but the actual
     * probed file will include the full name with absolute path AND 
     * the .jar extension.
     * 
     * @param hostCommand
     * @return
     */
    String expandToolName(String hostCommand) {
        int index = indexOfTool(hostCommand, name)
        int origIndex = index
        String foundName = name
        if(index < 0 && ('installExe' in config)) {
            index = indexOfTool(hostCommand, config.installExe)
            if(index >= 0) {
                foundName = config.installExe
                origIndex = index
            }
        }
        
        assert index >= 0 : "Tool name $name is expected to be part of command $hostCommand"
        if(index <0) {
            log.error("Internal error: Tool $name was probed but could not be found in the command the triggered it to be probed: $hostCommand")
            return name
        }
        
        while(index>0 && NON_PATH_TOKEN.indexOf(hostCommand.charAt(index-1) as int)<0)
            --index
        
        if(hostCommand.indexOf(foundName+".jar") == origIndex)
            return hostCommand.substring(index, origIndex+foundName.size()+4)
        else
            return hostCommand.substring(index, origIndex+foundName.size())
    }
    
    /**
     * Delimiters that tokenise a path
     */
    static String PATH_DELIMITERS = '/ \t;'
    
    /**
     * Delimiters that end a file name
     */
    static String FILE_END_DELIMITERS = ' \t;'
    
    /**
     * Width for printing out message headers during install process
     */
    static int PRINT_WIDTH = 100
    
    /**
     * Determine the index of the tool specified by <code>name</code>
     * in a command. Searches for a reference to <code>name</code> that
     * is delimited by path delimiters on either side so that
     * substrings of other commands are not falsely detected.
     * 
     * @param command   command to search for name
     * @param name      name of tool to search for
     * @return
     */
    static int indexOfTool(String command, String name) {
        int start = 0
        while(true) {
            int index = command.indexOf(name, start)
            if(index < 0)
                return -1
            
            String jarredName = name
            if(command.indexOf(name+".jar") == index) {
                jarredName = name + ".jar"
            }
            
            start = index + name.size()
            
            // Character before must be a delimiter
            if(index>0) {
                if(PATH_DELIMITERS.indexOf(command.charAt(index-1) as int)<0)
                    continue
            }
            
            // Character after must be a delimiter
            if(index<command.size()-1-name.size()) {
                if(FILE_END_DELIMITERS.indexOf(command.charAt(index+jarredName.size()) as int)<0)
                    continue
            }
            return index
        }
        return -1
    }
    
    String getFullName() {
        if(module)
            return "$module/$name"
        else
            return "$name"
    }
    
    def ask(String msg, Closure c) {
        if(stdin == null)
            stdin = System.in.newReader()
            
        print msg + "? (y/n) "
        String answer = stdin.readLine().trim()
        if(answer == "y") {
            c()
        }
    }
    
    int sh(String script, Map env = [:]) {
        
        File dir = new File(Config.config.script).absoluteFile.parentFile
        
        String rTempDir = Utils.createTempDir().absolutePath
        File tempScript = new File(rTempDir, "BpipeInstallScript.sh")
        tempScript.setExecutable(true)
        tempScript.text = """
        #!/bin/bash
        set -e
        
        """ + script
        ProcessBuilder pb = new ProcessBuilder([
           "bash", tempScript.absolutePath
        ] as String[]).directory(dir)
            
        Map pbEnv = pb.environment()
        env.each { k,v ->
            pbEnv[k] = String.valueOf(v)
        }
                          
        Process process = pb.start()
        process.consumeProcessOutput(System.out, System.err)
                
        return process.waitFor()
    }
    
    void hd(msg) {
        println("\n"+(" " + msg + " ").center(PRINT_WIDTH,"=") + "\n")
    }
    
    boolean isRPackage() {
        return ("installExe" in config) && (config.installExe == "Rscript")
    }
    
    boolean isPipPackage() {
        return ("installExe" in config) && ("pip" in config.installExe.split(",")*.trim())
    }
  
    boolean isCondaPackage() {
        return ("installExe" in config) && ("conda" in config.installExe.split(",")*.trim())
    }
     
    List<String> install() {
        
        File dir = new File(Config.config.script).absoluteFile.parentFile
        
        List<String> errors = []
        
        hd name
        
        String installExePath = null
        if(("installPath" in config) && ("installExe" in config)) {
            installExePath = "$dir/$config.installPath/$config.installExe"
        }
        
        log.info "Install exe path = " + installExePath
        
        String version
        String toolPath = name
        
        // If the exe we are installing is specified and it exists, then probe 
        // its version, otherwise skip probing
        if(!installExePath || new File(installExePath).exists()) {
            
            // Look for it in the default PATH
            version = probe("$name")
            
            if(version?.startsWith("Unable to determine version")) {
                errors << version
                return errors
            }
            
            if(version) {
                println "Probe found $name version $version " 
                return errors
            }
       
            if(isRPackage()) {
                return installRPackage()
            }
            
            if(isPipPackage()) {
                def pipErrors =  installPipPackage()
                
                // If pip fails but the package could possibly be installed by Conda,
                // then try that.
                if(pipErrors.isEmpty() || !isCondaPackage())
                    return pipErrors
            }
            
            if(isCondaPackage()) {
                return installCondaPackage()
            }
           
            if(!installExePath) {
                errors << "Unable to install tool $name as installPath and installExe are not both configured in the tool database."
                return errors
            }
            
            probed = false // otherwise it won't try again
            toolPath = installExePath
            version = probe(toolPath)
        }
        
        if(version) {
            println "Probe found $name version $version at $toolPath"
            return 
        }

        println "\n$name is not found / compiled / installed."

        if("install" in config) {

            ask("Do you want to install $name") {

                if("license" in config) {
                    println config.license
                    println ""
                    ask("Do you wish to continue downloading / installing $name") {
                        sh config.install
                    }
                }
                else {
                    sh config.install
                }
            }
            
            version = probe(installExePath)
            if(!version)
                errors << "Tool $name did not probe successfully even after installation. Please check for error messages above."
            
            return errors
                
        }
        else {
            def error = "Tool $name is not installed, and I don't know how to install it. Please install $name manually."
            errors << error
            return errors
        }
        

        /*
        for(Map.Entry check in config.check) {

            boolean result = false
            if(check.value instanceof String || check.value instanceof GString) {
                try {
                    result = (sh(check.value) == 0)
                }
                catch(Exception e) {
                    // failed to execute command
                    e.printStackTrace()
                }
            }
            else
            if(config.check.value instanceof Closure){
                result = config.check.value()
            }
            else {
                System.err.println "ERROR: install script error - was expecting $check.key to refer to either a String or a Closure but it was a ${check.value.class.name}"
                System.exit(1)
            }

            println check.key.padRight(PADDING) + ": " + (result?"yes":"no")

            if(!result) {
                def error = "ERROR: check $check.key for tool $name failed"
                errors << error
            }
        }
        */
    }
    
    List<String> installPipPackage() {
        
        String python = Utils.resolveExe("python",null)
        String pip = "pip"
        if(python != null) 
            pip = new File(python).absoluteFile.parentFile.absolutePath + "/pip"
        
        println "Installing  python package via pip: $name"
        
        int exitCode = sh """$pip install -q $name"""
        if(exitCode != 0)
            return ["Python package $name installation failed, please check for errors in output"]
        else
            return []
    }
    
    List<String> installCondaPackage() {
        
        String python = Utils.resolveExe("python",null)
        String conda = "conda"
        if(python != null) 
            conda = new File(python).absoluteFile.parentFile.absolutePath + "/conda" 
            
        println "Installing  python package via conda: $name"
        
        int exitCode = sh """$conda install -y $name"""
        if(exitCode != 0)
            return ["Python package $name installation failed, please check for errors in output"]
        else
            return []
    }
  
    
    List<String> installRPackage() {
        println "Installing R package: $name"
        
        String rscriptExe = Utils.resolveRscriptExe()
        
        int exitCode = sh """
$rscriptExe - <<!

local({r <- getOption("repos")
       r["CRAN"] <- "http://cran.r-project.org" 
       options(repos=r)
})

source("https://bioconductor.org/biocLite.R")
biocLite("$name")
!
"""
        if(exitCode != 0)
            return ["R package installation failed, please check for errors in output"]
        else
            return []
    }
}


