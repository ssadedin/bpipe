/*
 * Test that inputs with extensions find the correct file
 * (regression test for bug where output file is found as input)
 */
hello = {
    exec "echo foo > test.vcf"
}

filter_with_ext = {
    filter("fix") {
        msg "input = $input.vcf output = $output"
        exec "cp $input.vcf $output"
    }
}


Bpipe.run {
	hello + filter_with_ext 
}
