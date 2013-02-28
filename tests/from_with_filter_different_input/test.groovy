/*
 * In this test, we declare a filter which by default operates on the first input,
 * resulting in an expectation that a "bam" is being filtered to a "bam" output.
 * However then in the actual command the output is declared to be a different type
 * (vcf). Bpipe should accept this *only* because vcf was an input that was also 
 * used.
 */
hello = {
    from("bam") {
      filter("clean") {
        exec """
              cat $input.bam $input.vcf > $output.vcf
        """
      }
    }
}

run {
    hello 
}
