/*
 * Simplest possible test using inputs and outputs
 * join them together in a pipeline
 */
hello = {
  transform("f1") {
    exec "cp $input $output"
  }
}

world = {
  transform(["f2", "f3"]) {
    exec "cp $input $output1"
    exec "cp $input $output2"
  }
}

world2 = {
  transform("f2", "f3") {
    exec "cp $input $output1"
    exec "cp $input $output2"
  }
}

Bpipe.run {
	hello + world2
}
