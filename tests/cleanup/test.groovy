/*
 * A simple pipeline with some intermediate files to test
 * cleanup. Note that the last output (xls) depends only on the output
 * of the first stage (html), but not on the intermediate ones (csv, xls).
 * So we should be able to update the csv,xls files without actually requiring a 
 * rebuild of the last stage.
 */
@Transform("html")
hello = {
	exec "cp $input $output"
        Thread.sleep(1100)
}

@Transform("csv")
there = {
	exec "cp $input $output"
        Thread.sleep(1100)
}

@Transform("xml")
world = {
	exec "cp $input $output"
        Thread.sleep(1100)
}

@Transform("xls")
take = {
  from("html") {
    exec "cp $input $output"
    Thread.sleep(1100)
  }
}

run {
	hello + there + world + take
}
