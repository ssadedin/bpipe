/*
 * Running a stage multiple times in parallel but customized with different
 * parameters passed should correctly run the stages with different values for the
 * parameter variable.
 * 
 * Note: see Issue 57: http://code.google.com/p/bpipe/issues/detail?id=57
 */
hello = {
  msg "foo = $foo"
}


Bpipe.run {
	"s_%.txt" * [ hello.using(foo:"cat"), hello.using(foo:"bar") ] 
}
