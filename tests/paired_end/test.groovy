
REF="ref.fa"

paired = {
    from("fastq","fastq","fa") {
        exec "cat $input1 $input2 $input3 > $output.bam"
    }
}

run { paired }
