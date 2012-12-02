
hello = {
  exec "cp $input.txt $output.csv"
}

run { 
  chr(1..5, filterInputs:true) * [ hello ]
}
