hello = {
  exec "cat $input2.txt $input1.csv > $output.xml" 
}

run { hello }
