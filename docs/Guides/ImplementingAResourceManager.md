# How to implement an adapter script to support a Resource Manager

### Implementing a Resource Manager

Bpipe is designed to make adapting it to work within your own resource management system very easy.

A custom resource manager script is a single Bash shell script that supports three commands:

- start
- stop
- status

### The Start Command

The start script is passed parameters describing the job as environment variables. Using those, it needs to start the job and return an identifier (such as a Job ID) describing the job. This identifier is then used from that point on by Bpipe to refer to the job with your script.

The environment variables that Bpipe passes to your start script are as follows:

<table>
  <tr><td>JOBDIR</td><td>A directory that your script can safely write temporary files to</td></tr>
  <tr><td>COMMAND</td><td>The actual command that should be executed by the resource manager to execute the job</td></tr>
  <tr><td>ACCOUNT</td><td>An account (optional) that can be passed to the resource manager</td></tr>
  <tr><td>WALLTIME</td><td>The maximum time the job should be allowed to run, in the format hours:minutes:seconds</td></tr>
  <tr><td>MEMORY</td><td>The maximum amount of memory to be allocated for the job</td></tr>
  <tr><td>PROCS</td><td>The number of CPUs or processor cores to be allocated for the job</td></tr>
  <tr><td>QUEUE</td><td>If the resource manager has multiple queues, the queue may be configured by the user in Bpipe, and passed through in this variable</td></tr>
</table>

If the start script does not successfully schedule a job to run it **must** return a non-zero exit code.

### The Stop Command

The stop script is passed the job ID returned by the start script as an argument. It should cause the job to be cancelled if it is running, or do nothing if the job is already stopped. It should return a non-zero exit code only if the attempt to cancel the job fails. If an invalid job ID is supplied it should output "Unknown Job Id".

### The Status Command

The status command is passed a Job ID and should output to standard output one of 4 possible expressions:

- QUEUEING
- RUNNING
- COMPLETE `<`status`>`
- UNKNOWN

The `<`status`>` value should be the exit code of the original command from job itself: 0 if it exited successfully and non-zero if it did not execute successfully.