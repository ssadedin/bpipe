
annotate_test = {
    produce("hello.${branch}.txt") {
        exec "touch $output1"
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
