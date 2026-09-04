/*
 * Test that output metadata is tracked in SQLite database
 * and that cleanup and query commands still work correctly.
 */
hello = {
	exec "echo hello > $output"
}

world = {
	exec "cat $input > $output"
}

run {
	hello + world 
}