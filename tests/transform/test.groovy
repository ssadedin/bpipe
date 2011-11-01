/*
 * Simplest possible test - just execute a couple of commands and 
 * join them together in a pipeline, with transform.
 */
hello = {
	transform("csv") {
		exec "cp $input $output"
	}
}

@Transform("xml")
world = {
	exec "cp $input $output"
}

Bpipe.run {
	hello + world 
}
