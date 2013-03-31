/**
 * Test that when an outer filter is specified, that filter
 * can then still resolve on inputs that are only specified inside
 * an inner statement (effectively, one that is resolved after the
 * initial filter occurred.
 */
@transform("bed")
vcf_to_bed = {
        exec """
             cat $input.vcf > $output.bed
        """
}

@filter("extract")
extract_bam_bed = {
    exec """
        cat $input.bed $input.bam > $output.bam
    """
}

// NOTE: some alternative forms of this (not exercised by default)
// are worth testing if this test regresses in the future:
/*

extract_bam_bed = {
  filter("extract") {
    // from("bam","bed") {
        exec """
            cat $input.bed $input.bam > $output.bam
        """
    // }
  }
}

*/


run {
    vcf_to_bed + extract_bam_bed
}

