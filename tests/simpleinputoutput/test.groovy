/*
 * Simplest possible test using inputs and outputs
 * join them together in a pipeline
 */
hello = {
	exec "cp $input $output"
}

world = {
	exec "cp $input $output"
}

Bpipe.run {
	hello + world 
}
