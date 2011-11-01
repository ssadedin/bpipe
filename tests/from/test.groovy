/*
 * Test that from correctly finds an input from backwards in the pipeline
 * and makes it referencable via $input
 */
hello = {
	from("txt") {
		exec "cp $input $output"
	}
}

world = {
	from("csv") {
		msg "$input => $output"
		exec "cp $input $output"
	}
}

Bpipe.run {
	hello + world 
}
