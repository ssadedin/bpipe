/*
 * Simplest possible test - just execute a couple of commands and 
 * join them together in a pipeline
 */
hello = {
        println "foo = $foo"
}

bar = {
        println "foo = $foo"
}


Bpipe.run {
	hello.using(foo:"cat") + hello.using(foo:"tree") + bar.using(foo:"fubar")
}
