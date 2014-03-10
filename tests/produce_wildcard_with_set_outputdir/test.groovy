//
// This a test for a regression involving a subtle bug regarding
// setting the output directory and then referencing it directly
// immediately afterwards, in a situation when no output is 
// actually resolved anywhere by any command.
// The problem arises because the $output.dir actually resolves 
// the directory by looking at the output files. No output files
// means no way to resolve the directory.
//

simulate = {

    requires batch_name : "Please specify the batch name for this simulation"

    output.dir="${batch_name}_simulated_cnvs"

    produce(output.dir+"/${female}_*.bam") {
        exec """

            touch ${batch_name}_simulated_cnvs/${female}_test.bam

            """
    }
}

female = "XXXXX"

run {
  simulate
}
