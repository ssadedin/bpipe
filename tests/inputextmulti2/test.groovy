/*
 * Simplest possible test using inputs and outputs
 * join them together in a pipeline
 */
hello = {
	msg "inputs.txt: cat $inputs.txt > $output"
	msg "input.txt:  cat $input.txt > $output"
	msg "inputs:  cat $inputs > $output"
	msg "input:  cat $input > $output"

	msg "input[0]:  cat ${input[0]} > $output"
	msg "inputs[0]:  cat ${inputs[0]} > $output"
	// exec "cat $inputs > $output"
}

Bpipe.run {
	hello 
}
