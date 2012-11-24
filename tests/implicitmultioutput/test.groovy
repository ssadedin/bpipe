/*
 * Simplest possible test using inputs and outputs
 * join them together in a pipeline
 */
hello = {
	exec "cp $input $output"
}

world = {
	exec "cp $input $output1"
}

there = {
	exec "cp $input $output2"
}

run {
	hello + world + there
}
