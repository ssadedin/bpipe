hello = {
  exec "cp $input1.txt $output1.csv; cp $input2.txt $output2.csv"
}

run { hello }
