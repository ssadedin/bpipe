
align = {
  exec "cat $input1 $input2 > $output"

}

dedupe = {
    filter("foo") {
      msg "$input.txt => $output"
      exec "cat $input.txt > $output"
    }
}

Bpipe.run {
  "s_%_*.txt" * [ align + dedupe ] 
}
