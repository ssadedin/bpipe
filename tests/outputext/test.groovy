/*
 * Simplest possible test using inputs and outputs
 * join them together in a pipeline
 */
hello = {
    exec "cp $input $output.csv"
}

world = {
    println "$input => $output.txt, $output.xml"
    exec "echo execute1; cp $input $output.txt; cp $input $output.xml"
    println "$input => $output.tsv"
    exec "echo execute2; cp $input $output.tsv"
}

there = {
    produce("test.foo", "test.bar") {
      exec "echo execute3; touch $output.foo; cp $input $output.bar"
    }
}

run {
    hello + world + there
}
