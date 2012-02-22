/*
 * Simplest possible test - just execute a couple of commands and 
 * join them together in a pipeline
 */
hello = {
	exec "echo hello > test.txt"
  exec "sleep 60"
}

world = {
	exec "echo world > test.world.txt"
  exec "sleep 60"
}

Bpipe.run {
	hello + world 
}
