hello = {
  exec "cp $input.txt $output.csv"
}

run {
  ~"test_([a-z]*)\\.txt" * [ hello ]
}
