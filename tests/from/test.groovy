/*
 * Simplest possible test - just execute a couple of commands and 
 * join them together in a pipeline
 */
hello = {
	filter("foo") {
		exec "cp $input $output"
	}
}

@Filter("bar")
world = {
	exec "cp $input $output"
}

Bpipe.run {
	hello + world 
}
