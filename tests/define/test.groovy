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


defined = Bpipe.define { hello + world }

defined2 = Bpipe.define { jupiter + saturn }


Bpipe.run {
	defined + defined2 + mars
}
