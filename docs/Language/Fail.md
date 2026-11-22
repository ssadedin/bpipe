## The fail statement

### Synopsis

    
    fail <message>
    fail [text {<text>} | html { <html> } | report(<template>)] to <notification channel name>
    fail [{<text>} | html { <html> } | report(<template>)](text) to channel:<channel name>, 
                                                               subject:<subject>, 
                                                               file: <file to attach> 
 
### Availability

0.9.8.6_beta_2 +

### Behavior

Causes the current branch of the pipeline to terminate explicitly with a failure status and a provided message.

In the most simple form, a short message is provided as a string. The longer forms allow a notification or report to be generated as a result of the success.

While using `fail` as a stand alone construct is possible, the primary use case is to embed it inside the otherwise clause of a [check](/Language/Check) command, which ensures that Bpipe remembers the status and output of the check performed.

The message reported to the user is prefixed with the name of the stage that failed and, if the stage
is running inside a named branch (for example, a branch per sample), the name of that branch, eg:

```
Pipeline stage call_variants in branch sample1 aborted with the following message:

Sample sample1 has no variants - processing cannot continue
```

The simple form can also be used at the top level of a pipeline script, outside of any stage, for example to
validate parameters before the pipeline starts. In that case the message is reported as-is, since there is no
stage or branch context. `fail <message>` is equivalent to `throw new bpipe.PipelineError(<message>)`, but
with the added context described above.

*Note*: see the [send](/Language/Send) command for more information and examples about the variants of this command that send notifications and reports.

### Examples

**Cause an Explicit Failure of the Pipeline**
```groovy 

   fail "Sample $branch.name has no variants - processing cannot continue"
```

**Validate a parameter before the pipeline runs**
```groovy
var MODE : 'exome'

if(!(MODE in ['exome','genome']))
    fail "Unknown MODE $MODE: please specify exome or genome"
```
