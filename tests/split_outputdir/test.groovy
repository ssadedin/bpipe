hello = {
  exec """
    cp $input.txt $output.csv
  """
}

run {
  "%/test.txt" * [ hello ]
}
