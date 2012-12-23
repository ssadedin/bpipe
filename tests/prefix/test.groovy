/*
 * Simplest possible test - just execute a couple of commands and 
 * join them together in a pipeline
 */
hello = {
  produce("test.txt.hello","test.txt.csv") {
    exec "echo hello > $output"
    exec "echo hello > ${output.prefix}.csv"
  }
}

world = {
  from("test.txt.hello") {
    exec "cp $input.prefix ${output.prefix}.txt.world"
  }
}

run {
	hello + world 
}
