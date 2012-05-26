/*
 * Simplest possible test - just execute a couple of commands and 
 * join them together in a pipeline
 */

FOO="tree"
hello = {
	msg "FOO=$FOO"
	exec "echo hello > test.txt"
}

world = {
	exec "echo world > test.world.txt"
}

Bpipe.run {
	hello + world  + x
}
