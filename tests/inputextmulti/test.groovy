/*
 * Input file extensions should enable
 * a filtered selection of input files to be processed from multiple
 * file types based on file types
 */
hello = {
    produce(["foo.txt", "foo.csv", "bar.txt"]) {
	exec "cp $input $output1"
	exec "cp $input $output2"
	exec "cp $input $output3"
    }
}

world = {
	exec "cat $inputs.txt > $output"
}

Bpipe.run {
	hello + world 
}
