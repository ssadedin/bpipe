# The succeed statement

### Synopsis

```    
    
    succeed <message>
    succeed [text {<text>} | html { <html> } | report(<template>)] to <notification channel name><pre>
    succeed [{<text>} | html { <html> } | report(<template>)](text) to channel:<channel name>, 
                                                               subject:<subject>, 
                                                               file: <file to attach> 
```
 
### Availability

0.9.8.6_beta_2 +

### Behavior

Causes the current branch of the pipeline (or the whole pipeline, if executed in the root branch) to terminate with a successful status, but without producing any outputs.

In the most simple form, a short message is provided as a string. The longer forms allow a notification or report to be generated as a result of the success.

The `succeed` command allows you to have a branch of your pipeline terminate without continuing or feeding outputs into any following stages. This is mostly useful in situations where you have many parallel stages running. In a normal case, Bpipe expects every parallel branch to produce an output, and will fail the entire pipeline if the expected outputs are not generated. Sometimes however, no outputs are legitimately produced and in that case you just want to stop processing in those branches that give no outputs while allowing others to continue without aborting the whole pipeline.

While using `succeed` as a stand alone construct is possible, the primary use case is to embed it inside the otherwise clause of a [check](Language/Check) command, which ensures that Bpipe remembers the status and output of the check performed.

*Note*: see the [send](Language/Send) command for more information and examples about the variants of this command that send notifications and reports.

### Examples

**Terminate Branch Successfully**
```groovy 

   succeed "Sample $branch.name has no variants"
```
