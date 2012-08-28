
align = {
  msg "Aligning $input"
  exec "cat $input1 $input2 > $output.csv"
}

dedupe = {
  msg "$input.txt => $output"
  exec "cp $input.txt $output"
}

Bpipe.run {
  "s_%_*.txt" * [ align + dedupe ] 
}
