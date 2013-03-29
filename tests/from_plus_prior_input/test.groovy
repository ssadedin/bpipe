/*
 * Check that a filter doesn't cause the inner from to resolve the 
 * "output" from the filter as an input for the inner content
 * (regression)
 */
scan_misalignments = {
  filter("ma") {
    from("*.bam") {
        exec """
          cat $input.vcf $input1.bam > $output.bam
        """
    }
  }
}

run { scan_misalignments }
