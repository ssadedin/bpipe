package bpipe

import groovy.cli.commons.CliBuilder
import groovy.cli.commons.OptionAccessor
import groovy.transform.NamedDelegate
import groovy.transform.NamedVariant
import java.util.regex.Pattern

class AboutDetails {
    String title
}

class BpipeScriptBase extends groovy.lang.Script {
    
    void requires(Map requirements) { }
    
    void about(Map details) { }

    void doc(String details) {}

    void doc(Map details) {}
    
    void load(String path) { }
    
    void run(Closure c) { }
    
    void config( @DelegatesTo(ConfigObject) Closure c) { }

    void options( @DelegatesTo(CliBuilder) Closure c) { }
    
    void produce(String file, Closure c) { }
    void produce(List<String> files, Closure c) { }
    void produce(String file, String file2, Closure c) { }
    void produce(String file, String file2, String file3, Closure c) { }
    void produce(String file, String file2, String file3, String file4, Closure c) { }
    
    void exec(String cmd) { }
    void exec(String cmd, String config) { }
    
    void transform(String file, Closure c) { }
    void transform(String file, String file2, Closure c) { }
    void transform(String file, String file2, String file3, Closure c) { }
    void transform(String file, String file2, String file3, String file4, Closure c) { }
    
    void from(String file, Closure c) { }
    void from(String file, String file2, Closure c) { }
    void from(String file, String file2, String file3, Closure c) { }
    void from(String file, String file2, String file3, String file4, Closure c) { }
    void from(List files, Closure c) { }
    
    void fail(String message) { }

    void succceed(String message) { }

    Sender succeed(Sender s) { }
    
    Sender html(Closure c) { }
    
    Sender issue(Map details) { }
 	
    Sender text(Closure c) { }
    
    Sender json(Object obj) { }
    
    Sender json(Closure c) { }
    
    Sender jms(Closure c) { }
    
    Sender report(String reportName) { }
    
    Sender send(Sender s) { return s }
    
    Checker check(String name, Closure c) { }
    
    Checker check(Closure c) { }
    
    void uses(Map resources, Closure c) {
    }
    
    void filter(String file, Closure c) { }

    TransformOperation transform(String file) { }
    TransformOperation transform(String file, String file2) { }
    TransformOperation transform(String file, String file2, String file3) { }
    TransformOperation transform(String file, String file2, String file3, String file4) { }
    
    TransformOperation transform(Pattern file) { }
    TransformOperation transform(Pattern file, Pattern file2) { }
    TransformOperation transform(Pattern file, Pattern file2, Pattern file3) { }
    TransformOperation transform(Pattern file, Pattern file2, Pattern file3, Pattern file4) { }
    
    Map<String,Map<String,Map>> getInput() { }
    Map<String,Map<String,Map>> getOutput() { }
    
    Map<String,Map<String,Map>> getInputs() { }
    Map<String,Map<String,Map>> getOutputs() { }
    
    OptionAccessor getOpts() { }
 
    void var(Map values) {
    }
    
    File file(Object fileLike) {
    }
    
    List<String> getArgs() { }

    Branch getBranch() { }

    void forward(String... files) {}
    void forward(List<String> files) {}

    @Override
    public Object run() { return null; }

    public Object run(List<String> files, Closure c) { }
	
	public String getThreads() { }

	public String getMemory() { }

}
