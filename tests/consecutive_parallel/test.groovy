hello_a = {
	exec "echo hello_a > hello_a.txt"
}

@Produce("hello_b.txt")
hello_b = {
	exec "echo hello_b > hello_b.txt"
}

@Produce("hello_c.txt")
hello_c = {
	exec "echo hello_c > hello_c.txt"
}

hello_d = {
	exec "echo hello_d > hello_d.txt"
}

hello_e = {
	exec "echo hello_e > hello_e.txt"
}

hello_f = {
	exec "echo hello_f > hello_f.txt"
}

hello_g = {
	exec "echo hello_g > hello_g.txt"
}

hello_h = {
	exec "echo hello_h > hello_h.txt"
}

dummy = {
  forward input
}

/*
foo = segment {
    // hello_a + [hello_b, hello_c] + [hello_d, hello_e]
    hello_a + [hello_b, hello_c] + [hello_d, hello_e]
}
*/

run {
    // hello_a + [hello_b, hello_c] + [hello_d, hello_e]
    hello_a + [hello_b, hello_c] + [hello_d, hello_e]
}

