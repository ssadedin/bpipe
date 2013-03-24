/*
 * Test that when an @intermediate annotation is applied,
 * the outputs it applies to are cleaned up by the cleanup command,
 * EVEN when they appear to be "final" outputs, ie: they are
 * leaf nodes in the output graph.
 */
hello = {
  exec "cat $input.txt > $output.csv"
}

@intermediate
world = {
  exec "cat $input.csv > $output.xml"
}

@intermediate("*.xml")
world_with_filter = {
  exec "cat $input.csv > $output.xml"
}

@intermediate("*.tsv")
world_with_mismatch_filter = {
  exec "cat $input.csv > $output.xml"
}

run { hello + [ world, world_with_filter, world_with_mismatch_filter ] }

