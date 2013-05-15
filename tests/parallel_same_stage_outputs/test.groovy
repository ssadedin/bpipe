/*
 * Parallel segments that use the *same* stage
 * should work independently even though they are both running
 * the same stage, using the same thread (in this test,
 * Bpipe is run using -n 1 to force a single thread).
 */

how_are_you = {
  println "how_are inputs=$inputs"
  exec "sed 's/f/b/g' $input > $output"
}

take_me_to_your_leader = {
  println "take_me inputs=$inputs"
  exec "sed 's/f/g/g' $input > $output"
}

a = {
    exec "cp $input $output"
}

end = {
  println "end inputs=$inputs"
  exec "cat $inputs > $output"
}

// All the outputs should get forwarded to the 'end' stage
run {
	"s_%.txt" * [how_are_you + a, take_me_to_your_leader + a]  + end
}
