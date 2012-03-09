/*
 * Simplest possible test - just execute a couple of commands and 
 * join them together in a pipeline
 */
hello = {
	exec "echo hello > test.txt"
}

world = {
	exec "echo world > test.world.txt"
}

mars = {
	exec "echo mars > test.mars.txt"
}

jupiter = {
	exec "echo jupiter > test.jupiter.txt"
}

saturn = {
	exec "echo saturn > test.saturn.txt"
}


defined = segment { hello + world }

defined2 = segment { jupiter + saturn }


Bpipe.run {
	defined + all_external + defined2 + mars
}
