
align = {
  exec "cat $input1 $input2 > $output"

}

dedupe = {
    filter("foo") {
      msg "$input.align => $output"
      exec "cat $input.align > $output"
    }
}

Bpipe.run {
  "s_%_*.txt" * [ align + dedupe ] 
}
