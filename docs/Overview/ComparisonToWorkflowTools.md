# How Bpipe Compares to other Tools

The field of tools for managing computational workflows is quite crowded.  With many past failed attempts and many successful existing tools it can be hard to decide where any given tool fits.   Here we try to put Bpipe in context by comparing it to a number of similar tools.


## Feature Comparison Table

  |Tool|GUI|Command Line (`**`)|Audit Trail|Built in Cluster Support|Workflow Sharing|Online Data Source Integration|Need Programming Knowledge?|Easy Shell Script Portability|
  |:------|:-----:|:----:|:-----:|:----:|:-----:|:----:|:-----:|:----:| 
  |Bpipe|No|Yes|Yes|Yes|No|No|No|Yes|
  |Ruffus|No|Yes|Yes|No|No|No|Yes|No|
  |Galaxy|Yes|No|Yes|Yes|Yes|Yes|No|No|
  |Taverna|Yes|No|Yes|Yes|Yes|Yes|No|No|
  |Pegasus|Yes|Yes|Yes|Yes|Yes|Yes|Yes|No|


`**` Nearly all the tools have ways to execute things from the command line; what we are meaning here is whether that is the native way supported for the tool, and thus whether it is very easy and natural to use the tool that way.

As can be seen above, Bpipe's most unique feature is the 'Easy Shell Script Portability', which is  admittedly a somewhat made up category to represent Bpipe's strongest point:  if your bread and butter is running things on the command line then Bpipe is your shortest path to turning those into a robust pipeline.   What's more, you can do it with Bpipe without learning a 'programming language' as such;  Bpipe pipelines are declarative and  simple.  In general while Bpipe lacks the higher level features of many tools, its minimalist approach allows it to be substantially simpler and more flexible than other tools.  If that appeals to you, then Bpipe could be the right tool for you.

## Design Philosophy

Bpipe is different to most other tools in its design philosophy.  Most tools put a lot of emphasis on getting you to specify the files that will go into each stage of the process and the files that will come out.  Bpipe turns this philosophy on its head and says that specifying the files is something your pipeline tool should try to help you avoid rather than demand work from you to achieve. Since the idea of a pipeline is that data flows through the pipeline from one end to the other Bpipe's default assumption is that this is how you want your pipeline to work.  So by default Bpipe asks you to tell it as little as possible about the files going into or out of each stage.  Bpipe naturally forwards the files created by the previous stage to the next, and it automatically names output files for you based on the names of the stages they pass through.  Where you need a "non-default" file as input Bpipe lets you specify this implicitly through your command:  for example, if Bpipe sees $input.bam referenced it knows your pipeline stage is looking for a ".bam" file and finds the most recent BAM file that was output by a previous stage.  This implicit handling of inputs and outputs is one of the things that makes Bpipe easier to get up and running with than tools that ask you to go into depth describing your pipeline stages up front.  However Bpipe doesn't prevent you from specifying more about your inputs either. You can start simple and gradually add more specification, documentation and other features as your pipeline grows. This feature of letting you start simple and incrementally add the complexity and robustness as you need it is one of the main advantages of Bpipe.

## What about a GUI?

Please note that if you prefer using a GUI then at this stage Bpipe is not for you: there are excellent tools now that have more features than Bpipe that are more user friendly and which have a very wide acceptance.   Although Bpipe may get some graphical tools in the future (and already can make a diagram of your pipeline), the tools that already exist far exceed Bpipe's capabilities and will do for some time.
