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
  for(i in 0..10) { Thread.sleep(1000);  println "world $i" }
}

how_are_you = {
  msg "how_are inputs=$inputs"
  exec "sed 's/f/b/g' $input > $output"
  for(i in 0..10) { Thread.sleep(1000);  println "how_are $i" }
}

take_me_to_your_leader = {
  msg "take_me inputs=$inputs"
  exec "sed 's/f/g/g' $input > $output"
  for(i in 0..5) { Thread.sleep(1000);  println "take_me $i" }
}

end = {
  msg "end inputs=$inputs"
	exec "cat $inputs > $output"
}

// Stage world should execute in parallel with how_are_you 
// All the outputs should get forwarded to the 'end' stage
Bpipe.run {
	// hello + [world, take_me_to_your_leader + [how_are_you] ] + end
	hello + [world, [how_are_you, take_me_to_your_leader] ] + end
}
