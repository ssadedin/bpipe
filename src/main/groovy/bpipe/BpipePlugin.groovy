package bpipe

class BpipePlugin {
    
    String name
    
    File path
    
    public BpipePlugin(String name,File path) {
        this.name = name;
        this.path = path;
    }
    
    void configureCli(CliBuilder cli) {
    }
}
