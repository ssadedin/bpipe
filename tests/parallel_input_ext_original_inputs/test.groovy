// When multiple input types are provided 
// a reference to the input variable using an extension should
// search *all* the files specific to the parallel stage 
// in preference to the original inputs from the command line
// See bug 56

annotate_test = {
    produce("hello.txt") {
        //exec "touch $output1"
    }
}


create_variant_bam_test = {
    println "bamFile = $input.bam"
}


run {
     ".chr%."* [ 
            annotate_test  + 
            create_variant_bam_test
      ] 
}
