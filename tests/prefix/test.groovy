/*
 * Make sure using 'prefix' works on input and output variables
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
