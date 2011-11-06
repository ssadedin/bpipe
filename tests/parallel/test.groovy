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

world2 = {
  // produce("anotheroutput.txt") {
    exec "cp $input $output"
  // }
}

end = {
	exec "cat $inputs > $output"
}

// Stage world and world2 should execute in parallel
// Both outputs from world and world2 should be forwarded to the "end" stage
Bpipe.run {
	hello + [world,world2] + end
}
