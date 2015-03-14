# How Bpipe Probes for Versions of Tools Used

## Tool Versioning

A key part of making results reproducible is knowing not just the tools used to produce them but which versions of those tools were used.  Bpipe tries to provide some level of automated functionality for this in your pipelines.  This section describes how it works, and how you can add your own tools to the database.

## Tool Database

Unfortunately there is no universal standard for how a Unix tool should express what version of it is installed. Some tools support a "--version" flag but it is not always invoked the same way.  Others might just print the version as output if you run the tool with no arguments, or if you ask for the help output.

Due to this, Bpipe ships with a small database of popular bioinformatics tools for which tool meta data is built in, including information about how to determine the version of the tool.  You can extend this database yourself by adding a "tools" section to the ".bpipeconfig" section in your home directory.

Here is an example of tool information for the Genome Analysis Toolkit (GATK):

```groovy 

tools {
  GenomeAnalysisTK {
    probe = "java -Xmx64m -jar %bin% 2>&1 | grep 'Compiled' | grep -o 'v[-0-9\\.a-z]*'"
    link = "http://www.broadinstitute.org/gsa/wiki/index.php/Home_Page"
    desc = "A suite of tools for working with human medical resequencing projects"
  }
}
```

The `GenomeAnalysisTK` part that names the tool block here is critical: Bpipe does simple parsing of commands that are executed to match this token.  It is when this token is found in a command that Bpipe assumes that the tool is being executed in the command, and executes the `probe` command that is defined in the block to discover the version. The probe command, when executed, should simply print the version number of the tool.  In this case, it is necessary to execute Java with the GATK jar file to discover the actual version.  In general, you should use the %bin% token to refer to the actual binary rather than referring to it directly.  This is important in case the tool is not actually in the PATH and is being resolved with an absolute path in the pipeline script.

You can also add other meta data about tools, as illustrated by the "link" and "desc" sections in the tool description above. These are added to HTML reports that Bpipe produces about your scripts, and might be used in other ways as Bpipe develops.

If you develop tool metadata for commands you use, please consider donating them to the Bpipe open source project (to do so, contact us on the mailing list!).