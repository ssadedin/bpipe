# The Trash Folder

## The Trash Folder

In Bioinformatics especially, some data takes enormous amounts of resources to produce.  Unintentional deletion of a file could cost you weeks of work depending on the size of job you are undertaking and the amount of computing resources you have.  For this reason, Bpipe itself never deletes anything. *Ever*.  However part of the goal of Bpipe is to ensure that bad data (corrupt or failed outputs) never get confused with good data.  The best way to do that is to move anything that was not correctly produced right out of the way.  For this reason, Bpipe has the idea of a *Trash Folder*.

The Trash Folder is where Bpipe moves outputs that it considers potentially unclean or unsafe to use.  These include outputs that were created when a command was interrupted (eg: by being killed or using Ctrl-C), or if a command fails while creating the specified output.  The Trash Folder is created in the local directory under the name<code>.bpipe/trash</code>
Whenever Bpipe moves a file there, it will notify you in the output.  This way, if Bpipe "cleans up" a file that you actually wanted to keep, you can simply move it back out of the trash folder to the local directory.