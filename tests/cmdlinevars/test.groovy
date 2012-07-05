/*
 * Test that variables can be passed from the command line
 * and that defaults can be set
 */

bar="BAR"
fubar="FUBAR"

hello = {
        msg "foo=$foo"
	exec "echo $foo > test.txt"
}

world = {
        msg "bar=$bar"
	exec "echo $bar > test.world.txt"
}

there = {
        msg "fubar=$fubar"
	exec "echo $fubar > test.there.txt"
}


run {
	hello + world + there
}
