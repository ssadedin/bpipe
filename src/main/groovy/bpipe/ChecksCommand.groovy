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

class ChecksCommand {
    
    static void main(args) {
        
        if(!new File(".bpipe").exists()) {
            println ""
            println "Could not find a Bpipe pipeline in this folder: has Bpipe been run here?"
            println ""
            System.exit(1)
        }
        
        CliBuilder cli = new CliBuilder(usage: "bpipe override | bpipe checks", posix:true)
        cli.with {
            o "override specified check to force it to pass", args:1
        }
        
        List<Check> checks = Check.loadAll()
        List<Check> overrideChecks = null
        
        def opts = cli.parse(args)
        if(opts.o) {
            def parts = opts.o.split(/\./)
            if(parts.size() == 1) {
              overrideChecks = checks.grep { it.stage == parts[0] }
            }
            else {
              overrideChecks = checks.grep { it.stage == parts[0] && it.branch == parts[1] }
            }
            
            if(overrideChecks) {
                  overrideChecks.each { it.override = true; it.save() }
            }
            else {
                System.err.println ""
                System.err.println "Unable to find any checks matching name $opts.o"
                System.err.println ""
                System.exit(1)
            }
        }
       
        println "=" * Config.config.columns
        println "|" + " Checks ".center(Config.config.columns-2) + "|"
        println "=" * Config.config.columns
        println ""
        
        printChecks(checks)
        
        if(overrideChecks) {
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
                print "Overriding check ${check.stage} in branch ${check.branch}, OK (y/n)? "
                if(r.readLine() == "y") {
                    check.override = true
                    check.save()
                }
                println ""
            }
        }
    }
    
    static printChecks(List<Check> checks) {
        
        int count = 1
        
        println "Check".padRight(20) + " Branch".padRight(15) + " Status".padRight(15) + " Details".padRight(40)
        println "-" * 90
        
        println checks.collect {
               ((count++) + ". " + it.stage).padRight(20) +
               (" " + (it.branch!="all"?it.branch:"")).padRight(15) +
               (" " + (it.override?"Overridden":(it.passed?"Passed":"Failed"))).padRight(15) +
               (" " + (it.message?Utils.truncnl(it.message,30):"")).padRight(40)
        }*.plus('\n').join("")
        
        println ""
    }
}
