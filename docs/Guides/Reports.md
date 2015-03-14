# Creating HTML Reports

While Bpipe's logging is quite comprehensive, it is not usually something you can easily show to your boss or your non-technical collaborators. To address this need, Bpipe can generate reports in HTML format.  These reports by default show just run information - which stages executed, the files that were used as input and output, and the time window during which each stage ran.  Just this information can be useful, however you can add much more information to the report by annotating your pipeline with documentation and by supplementing the Bpipe tool database with information about the tools you use.  This section describes how to achieve this.

Here's a picture of an HTML report that was generated from a real pipeline:

http://wiki.bpipe.googlecode.com/git/report-screenshot.png

## Generating Reports

To generate a basic report, simply supply the "-r" argument to the Bpipe command line when you run your pipeline:
```groovy 

bpipe run -r pipeline.groovy file1.txt ... 
```

This will run the pipeline and then generate an HTML file called "index.html" in the "doc" directory in the same directory as the pipeline file.

## Customising Reports

### Title

To add a custom title to your report, simply add an "about" statement at the top of your pipeline file with a "title" attribute:
```groovy 

about title: "My Excellent Pipeline" 
```

### Pipeline Stages

The default report simply displays the pipeline stage names as they are in your pipeline file with no other information.  You can, however, add much more information if you like using a "doc" command inside the pipeline stage.  For example:
```groovy 

  align_with_bwa = {
      doc title: "Align FastQ files using BWA",
          desc:  """Performs alignment using BWA on Illumina FASTQ 
                    input files, producing output BAM files.  The BAM 
                    file is not indexed, so you may need to follow this
                    stage with an "index_bam" stage.""",
          constraints: "Only works with Illumina 1.9+ format.",
          author: "bioinformaticist@bpipe.org"
  }
```

For convenience there is also an abbreviated form for when you just want to set the title:
```groovy 

  align_with_bwa = {
      doc "Align FastQ files using BWA"
  }
```

### In Depth Customization

If you want to do really deep customization of your reports, you can edit the template that is used to generate the report.  You can find the template in the file: {{{<Bpipe installation directory>/html/index.html}}}.  This file is a dynamic [Groovy Template](http://groovy.codehaus.org/Groovy+Templates) that generates the HTML report from a model object that is passed in containing all the information about the pipeline stages that executed and their documentation. The attributes that you supply in the "doc" command and "about" command above are unconstrained, so you can make up your own custom ones if you wish and then reference them in your custom report as well.  Using this technique you can change the logo, color scheme or completely rearrange the whole report if you like.  If you do generate useful alternative reports, please consider contributing them back to the Bpipe project so that others can use them!