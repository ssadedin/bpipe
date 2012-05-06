/*
 * Simplest possible test using inputs and outputs
 * join them together in a pipeline
 */
hello = {
  msg " $input => $output"
	exec "cp $input $output"
}

fine = {
  msg " $input => $output"
  exec "cp $input $output"
  forward input
}

world = {
  msg " $input => $output"
	exec "if [ $input != './test.txt.hello' ]; then exit 1; else cp $input $output; fi;  "
}

run {
	hello + fine + world 
}
