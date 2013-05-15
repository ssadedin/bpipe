/*
 * Simplest possible test using inputs and outputs
 * join them together in a pipeline
 */
hello = {
	println "inputs.txt: cat $inputs.txt > $output"
	println "input.txt:  cat $input.txt > $output"
	println "inputs:  cat $inputs > $output"
	println "input:  cat $input > $output"

	println "input[0]:  cat ${input[0]} > $output"
	println "inputs[0]:  cat ${inputs[0]} > $output"

  exec "cat ${inputs[0]} > $output"
}

Bpipe.run {
	hello 
}
