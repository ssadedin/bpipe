/*
 * Simplest possible test using inputs and outputs
 * join them together in a pipeline
 */
hello = {
	exec "cp $input $output"
}

world = {
  msg "world inputs=$inputs"
  exec "cp $input $output"
}

how_are_you = {
  msg "how_are inputs=$inputs"
  exec "sed 's/f/b/g' $input > $output"
}

take_me_to_your_leader = {
  msg "take_me inputs=$inputs"
  exec "sed 's/f/g/g' $input > $output"
}

a = {
    exec "cp $input $output"
}

end = {
  msg "end inputs=$inputs"
	exec "cat $inputs > $output"
}

// Stage world should execute in parallel with how_are_you 
// All the outputs should get forwarded to the 'end' stage
Bpipe.run {
	"s_%.txt" * [world + [how_are_you + a, take_me_to_your_leader + a] ] + end
}
