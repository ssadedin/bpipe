/*
 * Tests that the @concurrency annotation limits the number of instances of a
 * stage that are allowed to run at the same time.
 *
 * The pipeline creates five branches, which with -n 5 would otherwise all run
 * at once.  The annotation on the stage caps this at two, so each instance
 * records the times at which it starts and finishes so that the test can count
 * the maximum number that overlapped.
 */
@concurrency(2)
hello = {
	exec "echo start $branch `date +%s%N` >> times.txt"

	exec "sleep 3"

	exec "echo end $branch `date +%s%N` >> times.txt"
}

run {
    ["one","two","three","four","five"] * [ hello ]
}
