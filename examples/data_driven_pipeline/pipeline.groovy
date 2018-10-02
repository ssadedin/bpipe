/**
 * This example shows how to construct a pipeline that reads the data
 * to process from a file.
 *
 * To run this pipeline, use:
 * 
 *     bpipe run pipeline.groovy  samples.txt 
 */

// We make use of the fact that the arguments to Bpipe are available in the args variable
sample_file = args[0]

println "Processing samples from $sample_file"

// Get the headers of our file
headers = new File(sample_file).readLines()[0].tokenize('\t')

samples = 
    new File(sample_file)
        .readLines()[1..-1] // read all the lines except first
        .collect { it.tokenize('\t') } // split each line by tab
        .collect { fields ->
            // transpose is like 'zip' in Python, this creates a Map with info about sample
            return [ headers, fields ].transpose().collectEntries() 
        }

println "Processing samples: " + samples*.sample_id.join(',') // note the * is groovy 'spread' operator

// The above is a List of samples (each sample being a Map / dictionary of info about the sample)
// However, it's nice to be able to look up any sample by its id, so let's index them that way

sample_index = Collections.synchronizedMap(samples.collectEntries { [ it.sample_id, it ] })

// For example, we can now do, what is the sex of S001?
// sample_index['S001'].sex

// Example: let's run something in parallel for each sample that is male:

process_male = {
    println "I am a male: $branch.name"

    // Set a branch variable so that downstream stages could see which sample we are processing
    branch.sample = sample_index[branch.name]

    // Forward the input file for this sample to later stages
    forward sample.file
}

some_analysis_step = {
    exec """
        cp $input.txt $output.txt
    """
}

males = samples.grep { it.sex == 'male' }*.sample_id 

run {
     males * [ 
        process_male + some_analysis_step
    ]
}



