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
  for(i in 0..10) { Thread.sleep(200);  println "world $i" }
}

how_are_you = {
  msg "how_are inputs=$inputs"
  exec "sed 's/f/b/g' $input > $output"
  for(i in 0..10) { Thread.sleep(200);  println "how_are $i" }
}

take_me_to_your_leader = {
  msg "take_me inputs=$inputs"
  exec "sed 's/f/g/g' $input > $output"
  for(i in 0..5) { Thread.sleep(200);  println "take_me $i" }
}

// @Filter("foo")
mars = {
  exec "sed 's/g/e/g' $input > $output"
}

end = {
  msg "end inputs=$inputs"
	exec "cat $inputs > $output"
}


index_bam = {
    produce(input+'.bai') {
        exec "cp $input " + input + '.bai'
    }
    forward input
}

seg = segment {
    hello + [ world + index_bam + [how_are_you, take_me_to_your_leader] + mars ]
}

// Stage world should execute in parallel with how_are_you 
// All the outputs should get forwarded to the 'end' stage
run {
	// hello + [world, take_me_to_your_leader + [how_are_you] ] + end
	"s_%.txt" * [ seg ] + end
}
