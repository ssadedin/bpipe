hello = {
    exec "echo hello > test.txt"
}

world = {
    exec "echo world > test.world.txt"
}

Bpipe.run {
    hello + world
}
