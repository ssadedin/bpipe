package bpipe

class ChecksCommand {
    
    static void main(args) {
        
        // List all the checks for the user
        def allFiles = Checker.CHECK_DIR.listFiles()
        
        def checks = allFiles.grep { !it.name.endsWith(".properties") }
        def props = checks.collectEntries { File checkFile -> 
            [ checkFile.name, allFiles.find { it.name == checkFile.name + ".properties" } ] 
        }
        
        println "=" * Config.config.columns
        println ""
        println " Checks ".center(Config.config.columns)
        println ""
        int count = 1
        println checks.collect { "  " + (count++) + ". " + it.name.split(/\./)[1] + " " + (Boolean.parseBoolean(it.text.trim())?"Failed":"Passed").padLeft(20)  }*.plus('\n').join("")
        println ""
        
        System.in.withReader { r ->
            while(true) {
                print "Enter a number of a check to override: "
                String answer = r.readLine()
                if(!answer.isNumber()) {
                    println "Please enter a number, or Ctrl-C to exit."
                    continue
                }
                
                int index = answer.toInteger()-1
                
                Map checkInfo = [ ["branch","name"], checks[index].name.split(/\./) ].transpose().collectEntries()
                println ""
                println "Overriding check ${checkInfo}"
                print "OK (y/n)? "
                if(r.readLine() == "y") {
                    Properties p = new Properties()
                    p.override = "true"
                    p.store(new File(Checker.CHECK_DIR, checks[index].name + ".properties").newWriter(), "Bpipe Check Properties")
                }
                println ""
            }
        }
    }
}
