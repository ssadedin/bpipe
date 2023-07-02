# How Bpipe Compares to other Tools

The field of tools for managing computational workflows is quite crowded.  With
many past failed attempts and many successful existing tools it can be hard to
decide where any given tool fits.   Here we try to put Bpipe in context by
comparing it to some similar tools.

## Key Comparison Tools

### WDL / Cromwell

WDL is the native workflow language supported by
[Cromwell](https://github.com/broadinstitute/cromwell). It is maintained as an
open standard, supported by the Broad Institute. WDL has some similarities to
Bpipe in that commands are able to be represented within the workflow definition
very similarly to how they appear if executed on the command line. This makes porting
existing command line based scripts to WDL relatively straight forward in most cases.

However, WDL is very different to Bpipe because of two specific things:

- it is a dedicated, custom language
- WDL is very static and rigid, requiring up front definition of pipeline 
  constructs and flow

In contrast to these two points: Bpipe uses a standard well known language to define pipelines (Groovy) and 
it is highly dynamic. That means you can write very flexible pipelines that are not easy or even possible to
express using WDL. If you value being able to use a commodity programming language, especially if you have complex
logic or integration needs for your pipelines, you may prefer Bpipe. On the other hand, if you value more highly
the use of an commonly accepted open standard for pipelines which other community members are using, and you don't
have complex integration or logic needs, this may lead you to prefer WDL.

## CWL (Common Workflow Language)

Like WDL, CWL is an open standard and is supported by multiple backend pipeline engines. CWL is different
to Bpipe and WDL in that it uses XML or JSON to define pipelines and in doing so, represents pipelines themselves
in a very data driven way. This is helpful if you are building tools and frameworks that need to read pipeline
information programmatically, for example to execute or visualise the pipeline flow. However, it is much less
ergonomic to write pipelines this way (unless you want to use a visual method, for example). If you primarily value
enabling highly non-technical users being able to create pipelines and you have an application or service
that can offer visual design of workflows, then CWL may be a good choice. However any type of
complex pipeline containing flexible or dynamic logic is likely to be easier to create using Bpipe.

## Nextflow

[Nextflow](https://nextflow.io/) is a powerful pipeline frameowrk that is actually very similar to Bpipe,
because it is also based on Groovy.  Indeed, historically the original author of
Nextflow contributed to Bpipe before branching off to create Nextflow, so the
conceptual similarities are not by accident. However, Nextflow has much greater
community support than Bpipe, including an excellent
[library](https://nf-co.re/) of community supported pipelines which represent
best practice versions of many available standard workflows.

Like Bpipe, Nextflow allows you more flexibility in creating pipelines because they are able to
be defined dynamically using logic, similarly to Bpipe. The differences between Nextflow and Bpipe
therefore are more subtle and often a matter of personal preference:

- Bpipe is still more dynamic than Nextflow because it allows you to completely control
  runtime logic at the time of pipeline stage execution
- Nextflow requires explicit definition of inputs and outputs, while Bpipe allows these
  to be implicit when they match the pipeline flow. This makes Bpipe easier and more 
  natural to write for simple cases, but it also means there is more implicit 
  behaviour which can become confusing in more complex situations.
- Nextflow has a strong design philosophy about how pipeline stages are executed, so that
  their results are always reproducible and also portable. While this has advantages, it 
  can be significantly less convenient. For example, Nextflow provides no way to see the
  output of a running pipeline, because it is assumed that it could be running on
  inaccessible infrastructure, while Bpipe always forwards output printed by pipeline
  stages to your console.
- When it comes to integration and full-service features, Bpipe comes with more "batteries included".
  For example, Bpipe has features for integrating with message queues, REST APIs, building reports
  in HTML or PDF format and other types of rich integration and output control. Nextflow focuses
  more on being a pure pipeline exeuction framework and leaves these types of features to
  users to implement themselves.

In most other respects Bpipe and Nextflow are comparable and which you might choose is a matter
of taste. However, if you strongly value the strength of the community supporting Nextflow then
that could be a powerful reason to select it instead of Bpipe.

## Design Philosophy

Another way to think about tool selection is to try and understand the design philosophy of the tools
you are looking at. When the underlying philosophy of the reason the tool was made matches your needs,
then it is likely to work out well. However when you differ on your needs and what the tool is meant to do,
you will often find you run into a lot of problems that can be hard to solve, even if the overall quality 
and ecosystem of the tool itself looks better.

So, what is Bpipe's design philosophy?

Bpipe tries hard to put working practitioners who spend their days wrangling commands
at the command line first. Therefore it provides highly convenient command line tools
that are designed to make it very convenient to use this way. It also 
tries hard to make it easy to convert sequences of
command line tools into robust and powerful pipelines. To do this, 
Bpipe tries very hard to remove the friction points in converting an existing 
command you might already know how to execute into a well defined, reusable
pipeline stage. It allows you to do this incrementally and gradually, starting with 
a plain command that is identical to what you might run manually and ending up with
something that is fully portable, robust and giving you many high grade pipeline
features through gradual enhancements that can be made over time.

If you value the capability to start simple with plain commands and gradually enhance
them to scale all the way up to a fully robust, production quality and well engineered,
portable pipeline, then Bpipe could be right for you!





