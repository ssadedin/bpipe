/*
 * Test that producing multiple explicit outputs works
 */
hello = {
  produce("foo.txt") {
    exec "cp $input $output"
  }
}

world = {
  produce("world.txt", "mars.txt") {
    exec "cp $input $output1"
    exec "cp $input $output2"
  }
}

run {
	hello + world
}
