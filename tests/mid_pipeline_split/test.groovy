
hello = {
  transform("csv","csv","tsv") {
    exec "cp $input1.txt $output1.csv; cp $input2.txt $output2.csv; cp $input1.txt $output3.tsv"
  }
}

world = {
  exec "cp $input.csv $output.xml"
}

run {
  hello + "test_%.csv" * [ world ]
}
