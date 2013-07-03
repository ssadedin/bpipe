/**
 * Bug found when running a single align stage with bam files as input
 * When using transform, Bpipe was happy to assign $output.bam the same value
 * as $input.bam which should never happen. Even though a
 * "transform" from bam => bam is not strictly correct / possible, 
 * Bpipe should absolutely not as a result produce an output file with the
 * same name as an input file.
 */

align = {
    transform("bam") {
        exec """
            cat $input.bam $input.bam > ${output.bam}
        """
    }
}

run {
    align
}
