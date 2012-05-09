/*
 * Simplest possible test - just execute a couple of commands and 
 * join them together in a pipeline
 */
hello = {
        msg "foo = $foo"
}

bar = {
        msg "foo = $foo"
}


Bpipe.run {
	hello.using(foo:"cat") + hello.using(foo:"tree") + bar.using(foo:"fubar")
}
