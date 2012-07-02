/*
 * Simplest possible test using inputs and outputs
 * join them together in a pipeline
 */
hello = {
	exec "cp $input $output.csv"
}

world = {
	exec "cp $input $output.txt; cp $input $output.xml"

	exec "cp $input $output.tsv"
}

there = {
    produce("test.foo", "test.bar") {
	exec "cp $input $output.bar"
    }
}

Bpipe.run {
	hello + world + there
}
