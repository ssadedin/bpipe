/*
 * Test the produce construct and the Produce annotation 
 */
hello = {
	produce('test.out_1') {
		exec "cp $input $output"
	}
}

@Produce("test.out_2")
world = {
	exec "cp $input $output"
}

Bpipe.run {
	hello + world 
}
