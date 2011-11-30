/*
 * Simplest possible test - just execute a couple of commands and 
 * join them together in a pipeline
 */
hello = {
	exec "echo hello > test.txt"
}

world = {
    R{"""
        read.table('$input')
	print("hello $input")
        x = c("cat","dog","tree")
        d = data.frame(x=c(1,2,3,4), y=c("cow","dog","tree","house"))
        print(d$x)
       """}
}

Bpipe.run {
	hello + world 
}
