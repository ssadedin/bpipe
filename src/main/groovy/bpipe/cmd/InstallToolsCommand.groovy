package bpipe.cmd

class InstallToolsCommand  extends BpipeCommand { 
    
    public InstallToolsCommand(List<String> args) {
        super("install", args); 
    }

    public void run(Writer out) {
        
        CliBuilder cli = new CliBuilder(usage: "bpipe install [options] <pipeline>")
        
        cli.with {
            v 'Verbose logging'
            q 'Quiet: auto-answer questions with default answers'
        }
        
        def opts = cli.parse(args)
        if(opts.arguments().size() > 0)
            bpipe.Config.config.script = opts.arguments()[0]
            
        if(opts.v)
            bpipe.Utils.configureVerboseLogging()
            
        if(opts.q) {
            out.println "Enabling quiet mode: you agree to all license conditions of installed tools"
            System.setProperty('bpipe.quiet', 'true')
        }
            
        bpipe.Runner.readUserConfig()
        bpipe.ToolDatabase.instance.init(bpipe.Config.userConfig)
        
        ConfigObject cfg = bpipe.Config.userConfig
        if("title" in cfg.install) {
            println ""
            println "*" *  bpipe.Tool.PRINT_WIDTH
            println "$cfg.install.title Install Script"
            println "*" * bpipe.Tool.PRINT_WIDTH
            println "" 
        }
        
        if(!("install" in cfg)) {
            out.println "No tools are currently configured in the tool database. Please check your installation for the tool database configuration"
            return
        }
        
        bpipe.ToolDatabase.instance.installTools(cfg.install.tools)
    }
}
