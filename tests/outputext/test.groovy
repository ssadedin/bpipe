/*
 * Simplest possible test using inputs and outputs
 * join them together in a pipeline
 */
hello = {
	exec "cp $input $output.csv"
}

world = {
	exec "cp $input $output.xml"
}

Bpipe.run {
	hello + world 
}
