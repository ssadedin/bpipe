/*
 * Simplest possible test using inputs and outputs
 * join them together in a pipeline
 */
hello = {
  filter("f1") {
    exec "cp $input $output"
  }
}

world = {
  filter(["f2", "f3"]) {
    exec "cp $input $output1"
    exec "cp $input $output2"
  }
}

world2 = {
  filter("f2", "f3") {
    exec "cp $input $output1"
    exec "cp $input $output2"
  }
}

Bpipe.run {
	hello + world2
}
