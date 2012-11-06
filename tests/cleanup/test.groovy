/*
 * A simple pipeline with some intermediate files to test
 * cleanup
 */
@Transform("html")
hello = {
	exec "cp $input $output"
}

@Transform("csv")
there = {
	exec "cp $input $output"
}

@Transform("xml")
world = {
	exec "cp $input $output"
}

@Transform("xls")
take = {
  from("html") {
    exec "cp $input $output"
  }
}

run {
	hello + take
}
