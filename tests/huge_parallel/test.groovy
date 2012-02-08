/*
 * The main test here is 100 threads / files all at once and whether that creates any
 * issues.
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

Bpipe.run {
	"input_%.txt" * [hello] + end
}
