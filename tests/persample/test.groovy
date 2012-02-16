
/*
@SplitBy("-%")
copy_to_resource_folder = {
  produce("output/$input") {
    exec "cp $input $output"
  }
}
*/

@Filter("align")
align = {
  exec "cat $input1 $input2 > $output"

}

@Filter("dedupe")
dedupe = {
  exec "cp $input $output"
}

compute_statistics = {
  exec "wc $inputs > $output"
}

hello_there = {
	exec """ echo "hello there" """
}

Bpipe.run {
  "s_%_*.txt" * [ align + dedupe, hello_there ] + compute_statistics 
}
