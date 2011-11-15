
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

Bpipe.run {
  "s_%_*.txt" * [ align + dedupe ] + compute_statistics 
}
