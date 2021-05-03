Welcome to Bpipe  ![Tests](https://github.com/ssadedin/bpipe/actions/workflows/ci-build.yml/badge.svg)
=================

Bpipe provides a platform for running big bioinformatics jobs that consist of a series of processing stages - known as 'pipelines'.

* January 2021 - New! [Bpipe 0.9.10](http://docs.bpipe.org/Releases/0.9.10/) released!
* Download [latest](https://github.com/ssadedin/bpipe/releases/tag/0.9.10)
* [Documentation](http://docs.bpipe.org)
* [Mailing List](https://groups.google.com/forum/#!forum/bpipe-discuss) (Google Group)

Bpipe has been published in [Bioinformatics](http://bioinformatics.oxfordjournals.org/content/early/2012/04/11/bioinformatics.bts167.abstract)! If you use Bpipe, please cite:

  _Sadedin S, Pope B & Oshlack A, Bpipe: A Tool for Running and Managing Bioinformatics Pipelines, Bioinformatics_

**Why Bpipe?**

Many people working with bioinformatics data end up running jobs as shell
scripts.  While this makes running them easy it has a lot of limitations.  For
example, when scripts fail half way through it is often hard to tell where, or
why they failed, and even harder to restart the job from the point of failure.
There is no automatic log of the commands executed or automatic capture of
console output to ensure it is possible to later on see what happened.
Sometimes jobs fail half way through, leaving half created files that can get
confused with good files.  Modifying the pipeline is also time consuming and
error prone - adding or removing a stage causes modifications in multiple
places, meaning that it is all too easy to have a change of file name cause
later commands to fail or worse, run on incorrect data.  Bpipe tries to solve
all these problems (and more!) while departing as little as possible from the
simplicity of the shell script.  In fact, your Bpipe scripts will often look
very similar to the original shell scripts you might have started with.

By turning your shell scripts into Bpipe scripts, here are some of the features
you can get:

  * **Simple definition of tasks to run** - Bpipe runs shell commands almost as-is - no programming required.
  * **Transactional management of tasks** - commands that fail get outputs cleaned up, log files saved and the pipeline cleanly aborted.  No out of control jobs going crazy.
  * **Automatic Connection of Pipeline Stages** -  Bpipe manages the file names for input and output of each stage in a systematic way so that you don't need to think about it.  Removing or adding new stages "just works" and never breaks the flow of data.
  * **Easy Restarting of Jobs** - when a job fails cleanly restart from the point of failure.
  * **Easy Parallelism** - Bpipe makes it simple to split jobs into many pieces and run them all in parallel whether on a cluster or locally on your own machine
  * **Audit Trail** - Bpipe keeps a journal of exactly which commands executed and what their inputs and outputs were.
  * **Integration with Cluster Resource Managers** - if you use Torque PBS, Oracle Grid Engine or Platform LSF then Bpipe will make your life easier by allowing pure Bpipe scripts to run on your cluster virtually unchanged from how you run them locally.
  * **Notifications by Email, Instant Message, Message Queuing Systems** - Bpipe can send you alerts to tell you when your pipeline finishes or even as each stage completes.
  * See how Bpipe compares to [similar tools](http://docs.bpipe.org/Overview/ComparisonToWorkflowTools/)

Take a look at the [Overview](http://docs.bpipe.org/Overview/Introduction/) to
see Bpipe in action, work through the [Basic Tutorial](http://docs.bpipe.org/Tutorials/Hello%2CWorld/) 
for simple first steps, see a step by step example of a [realistic
pipeline](http://docs.bpipe.org/Tutorials/RealPipelineTutorial/) made using Bpipe, or 
take a look at the [Reference](http://docs.bpipe.org) to see all the documentation.
