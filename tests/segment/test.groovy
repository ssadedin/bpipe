/*
 * Simplest possible test - just execute a couple of commands and 
 * join them together in a pipeline
 */
hello = {
    produce("test.txt") {
	exec "echo hello > test.txt"
    }
}

world = {
    produce("test.world.txt") {
	exec "echo world > test.world.txt"
    }
}

there = {
    produce("test.there.txt") {
	exec "echo there > test.there.txt"
    }
}

buddy = {
    produce("test.buddy.txt") {
	exec "echo buddy > test.buddy.txt"
    }
}

foo = segment {
    world + there + buddy
}

Bpipe.run {
    foo
}
