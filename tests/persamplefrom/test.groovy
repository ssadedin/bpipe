
/*
@SplitBy("-%")
copy_to_resource_folder = {
  produce("output/$input") {
    exec "cp $input $output"
  }
}
*/

align = {
  exec "cat $input1 $input2 > $output"

}

dedupe = {
	from("txt") {
		filter("foo") {
		  msg "$input => $output"
		  exec "cp $input $output"
		}
	}
}

Bpipe.run {
  "s_%_*.txt" * [ align + dedupe ] 
}
