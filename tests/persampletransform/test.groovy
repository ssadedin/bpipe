
/**
 * Check that a "chr" variable makes its way through a transform into 
 * the wrapped content.
 */
align = {
  transform("bam") {
    msg "$inputs => $output"
    exec "cat $inputs > $output"
  }
}

run {
  chr(1) * [ align ]
}
