/*
 * Simplest possible test using inputs and outputs
 * join them together in a pipeline
 */
hello = {
    exec "cp $input $output.csv"
}

world = {
    msg "$input => $output.txt, $output.xml"
    exec "cp $input $output.txt; cp $input $output.xml"
    msg "$input => $output.tsv"
    exec "cp $input $output.tsv"
}

there = {
    produce("test.foo", "test.bar") {
      exec "cp $input $output.bar"
    }
}

run {
    hello + world + there
}
