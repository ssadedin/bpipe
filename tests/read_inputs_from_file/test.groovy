//
// Example script to read samples from a file and process them in parallel
//

samples_file=args[0]

// What are the headers - read the first line
// no headers? define them like: headers = ['FOO','BAR', ... ] and remove line below, change data line to process all rows 
headers = new File(samples_file).readLines()[0].tokenize('\t')

// Parse the data from the file, this is creating a List of Maps (dictionary-like objects)
samples = new File(samples_file).readLines()[1..-1]*.tokenize('\t').collect { [ headers, it ].transpose().collectEntries() }

// This is using the groovy 'spread' operator to make a list of sample ids
sample_ids = samples*.SAMPLE_ID

// Can be useful to do validation at this point
if(sample_ids.unique() != sample_ids)
    throw new bpipe.PipelineError("Bad idea! you put duplicate samples into the sample file!")

// Who doesn't like banners?
println """
=====================================================

WELCOME TO AWESOME EXAMPLE PIPELINE

=====================================================
"""

// Hidden feature! Bpipe can print out Markdown tables, this is a little bit magical
bpipe.Utils.table(headers, samples.collect { it*.value })

println ""

init_sample = {

    println "Initialising sample $branch.name"

    // We can set the whole sample object as a property of the branch
    // by finding it within the samples list
    branch.sample = samples.find { it.SAMPLE_ID == branch.name }

    forward(branch.sample.FILES.tokenize(','))
}

real_stage = {

    println "I am processing files for $sample.SAMPLE_ID ($sample.SEX): $inputs.gz "

    // no this doesn't make sense
    exec """
        cat $inputs.gz > $output.txt
    """
}

println sample_ids

run {
    sample_ids * [ init_sample + real_stage ]
}
