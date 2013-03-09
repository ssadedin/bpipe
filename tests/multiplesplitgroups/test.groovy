hello = {
  msg "INPUTS: $input1 $input2"
}

run { 
  "s_%_ignore_%_*.txt" * [ hello ] 
}
