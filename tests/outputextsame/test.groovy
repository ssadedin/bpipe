// When multiple input types are provided 
// and both input and output extension are used with the same ext,
// the output should be a transform of the input used
test = {
            msg "input bam is $input.bam => $output.bam"
}

run {
         ".chr%."* [ 
                        test
           ] 
}
