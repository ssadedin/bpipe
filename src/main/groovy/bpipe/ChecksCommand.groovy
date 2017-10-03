/*
 * Copyright (c) 2014 MCRI, authors
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

import java.io.Writer

import bpipe.cmd.BpipeCommand
import bpipe.worx.WorxEventListener

class ChecksCommand extends BpipeCommand {
    
   public ChecksCommand(List<String> args) {
        super("checks", args);
    } 
    
    static void main(args) {
		
		Runner.initializeLogging("0", "command")
		
        System.out.withWriter { w ->
            new ChecksCommand(args as List).run(w)
        }
    }
    
    @Override
    public void run(Writer out) {
        
        if(!new File(".bpipe").exists()) {
            out.println ""
            out.println "Could not find a Bpipe pipeline in this folder: has Bpipe been run here?"
            out.println ""
            System.exit(1)
        }
        
        CliBuilder cli = new CliBuilder(usage: "bpipe override | bpipe checks", posix:true)
        cli.with {
            o "override specified check to force it to pass", args:1
            f "fail specified check", args:1
            c "comment to add to given operation", args:1
            l "list checks and exit, non-interactive mode"
            h "show help", longOpt: 'help'
        }
        
        List<Check> checks = Check.loadAll()
        List<Check> overrideChecks = null
        
        def opts = cli.parse(args)
        if(!opts || opts.h) {
            if(opts?.h)
                cli.usage() 
            System.exit(0)
        }
        
        if(opts.o || opts.f) {
            
            bpipe.Runner.readUserConfig()
            
            EventManager.instance.configure(Config.userConfig)  
           
            if(Config.userConfig.worx.enable) {
                new WorxEventListener().start()
                Thread.sleep(1000)
            }
            
			if(opts.o)
	        		setCheckState(checks, opts.o, opts.c?:null, true)
			
		    if(opts.f)
	        		setCheckState(checks, opts.f, opts.c?:null, false)
				
		    System.exit(0)
        }
        
        printChecks(checks)
        
        if(opts.l) {
            System.exit(0)
        }
        
        System.in.withReader { r ->
            while(true) {
                print "Enter a number of a check to override, * for all, Ctrl-C to exit: "
                String answer = r.readLine()
                
                if(answer == "*") {
                    print "\nOverriding ALL checks, OK (y/n)? "
                    if(r.readLine() == "y") {
                        checks.each { if(!it.passed) it.override = true; it.save()  }
                        println ""
                        printChecks(checks)
                    }
                    continue
                }
                
                if(!answer.isNumber()) {
                    println "Please enter a number, or Ctrl-C to exit.\n"
                    continue
                }
                
                int index = answer.toInteger()-1
                
                if(index >= checks.size()) {
                    println "Please choose a number between 1 and ${checks.size()}\n"
                    continue
                }
                
                Check check = checks[index]
                
                println ""
                print "Passing check ${check.stage} in branch ${check.branch}, OK (y/n)? "
                if(r.readLine() == "y") {
                    check.override = true
                    check.save()
                }
                println ""
            }
        }
    }
	
	void setCheckState(List<Check> checks, String checkId, String comment, boolean pass) {
		
		List<Check> overrideChecks
        List<String> parts = checkId.tokenize('.')
        if(parts.size() == 1) {
          overrideChecks = checks.grep { it.stage == parts[0] }
        }
        else {
          overrideChecks = checks.grep { it.stage == parts[0] && it.branch == parts[1] }
        }
            
        if(overrideChecks) {
            overrideChecks.each { Check check ->
                check.overrideCheck(pass, comment) 
            }
			out.println "Set check $checkId to " + (pass ? "pass" : "fail") + " state"
            Thread.sleep(1000)
        }
        else {
            out.println ""
            out.println "Unable to find any checks matching name $checkId"
            out.println ""
            System.exit(1)
        }
	}
    
    static printChecks(List<Check> checks) {
        printChecks([:],checks)
    }
    
    static printChecks(Map options, List<Check> checks) {
        
        def out = System.out
        if(options && options.out)
            out = options.out
        
        int columns = Config.config.columns
        if(options && options.columns)
            columns = options.columns
         
        int screenColumns = -1
        if(System.getenv("COLUMNS") != null) {
            screenColumns = System.getenv("COLUMNS").toInteger()
        }
        
        // Estimate maximum needed width 
        List headers = [" Check"," Branch"," Status"," Details"]
        List widths = [
              (checks.collect { it.name?:"" }+[headers[0]])*.size()*.plus(4).max(), 
              (checks.collect { it.branch?:"" }+[headers[1]])*.size().max(), 
             "Overridden".size(), 
             (checks.collect { it.message?:"" }+[headers[3]])*.size().max() 
       ].collect { it+2 }
        
        if(screenColumns > 0 && (widths.sum()+10)>screenColumns) {
            widths[-1] = Math.max(20, screenColumns - widths[0..-1].sum())
        }
        
        columns = widths.sum()  + 10
        
        int count = 1
        out.println "=" * columns
        out.println "|" + " Check Report ".center(columns-2) + "|"
        out.println "=" * columns
        int index = 1
        Utils.table(
            headers,
            
            checks.collect { check ->
                [
                    ((index++) + '. ').padLeft(4) + (check.stage + (check.name ? ' ' + check.name:'')),
                    check.branch,
                    check.override?"Overridden":(check.passed?"Passed":"Failed"),
                    check.message ?: ''
                ]
            },
            out: out,
            topborder: true
        )
        
        out.println ""
        
    }
}
