/*
 * Test that implicit input extensions work
 * Given multiple inputs at each stage it should select the second one
 */
hello = {
	produce(["hello.out.csv", "hello.out.txt"]) {
		exec "cp $input.csv ${output[0]}"
		exec "cp $input.txt ${output[1]}"
	}
}

world = {
	exec "cp $input.txt $output"
}

Bpipe.run {
	hello + world 
}
