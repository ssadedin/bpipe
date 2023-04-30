Welcome to Bpipe  ![Tests](https://github.com/ssadedin/bpipe/actions/workflows/ci-build.yml/badge.svg)
=================

<style type='text/css'> .col-md-3 { display: none; } </style>
 
Bpipe provides a platform for running data analytic workgflows that consist of a series of processing stages - known as 'pipelines'. Bpipe has special features to help with
specific challenges in Bioinformatics and computational biology.

* September 2021 - New! [Bpipe 0.9.11](https://github.com/ssadedin/bpipe/releases/tag/0.9.11) released!
* [Documentation](https://docs.bpipe.org)
* [Mailing List](https://groups.google.com/forum/#!forum/bpipe-discuss) (Google Group)

Bpipe has been published in [Bioinformatics](http://bioinformatics.oxfordjournals.org/content/early/2012/04/11/bioinformatics.bts167.abstract)! If you use Bpipe, please cite:

  _Sadedin S, Pope B & Oshlack A, Bpipe: A Tool for Running and Managing Bioinformatics Pipelines, Bioinformatics_

**Example**
  
```groovy
 hello = {
    exec """
        echo "hello world" > $output.txt
    """
 }
 run { hello }
```

**Why Bpipe?**

Many people working in data science end up running jobs as custom shell (or similar)
scripts.  While this makes running them easy it has a lot of limitations. 
By turning your shell scripts into Bpipe scripts, here are some of the features
you can get:

  * **Dependency Tracking** - Like `make` and similar tools, Bpipe knows what you already did and won't do it again
  * **Simple definition of tasks to run** - Bpipe runs shell commands almost as-is : super low friction between what works in your command line and what you need to put into your script
  * **Transactional management of tasks** - commands that fail get outputs cleaned up, log files saved and the pipeline cleanly aborted.  No out of control jobs going crazy.
  * **Automatic Connection of Pipeline Stages** -  Bpipe manages the file names for input and output of each stage in a systematic way so that you don't need to think about it.  Removing or adding new stages "just works" and never breaks the flow of data.
  * **Job Management** - know what jobs are running, start, stop, manage whole workflows with simple commands
  * **Easy Parallelism** - split jobs into many pieces and run them all in parallel whether on a cluster, cloud or locally. Separate configuration of parallelism from the definition of the tasks.
  * **Audit Trail** - keeps a journal of exactly which commands executed, when and what their inputs and outputs were.
  * **Integration with Compute Providers ** - pure Bpipe scripts can run unchanged whether locally, on
  your server, or in cloud or traditional HPC back ends such as Torque, SLURM GridEngine or others.
  * **Deep Integretation Options** - Bpipe integrates well with other systems: receive alerts to tell you when your pipeline finishes or even as each stage completes, call REST APIs, send messages to queueing systems and easily use any type of integration available within the Java ecosystem.
  * See how Bpipe compares to [similar tools](https://docs.bpipe.org/Overview/ComparisonToWorkflowTools/)

**Ready for More?**

Take a look at the [Overview](https://docs.bpipe.org/Overview/Introduction/) to
see Bpipe in action, work through the [Basic Tutorial](https://docs.bpipe.org/Tutorials/Hello-World/) 
for simple first steps, see a step by step example of a [realistic
pipeline](http://docs.bpipe.org/Tutorials/RealPipelineTutorial/) made using Bpipe, or 
take a look at the [Reference](http://docs.bpipe.org) to see all the documentation.
