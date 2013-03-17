hello = {
  exec """
    cat $input.txt > $output.csv
  """
}

there = {
  exec """
    cat $input.csv > $output.xml
  """
}

world = {
  exec """
    cat $inputs.csv > $output.txt
  """
}

run {
  "%.txt" * [ hello + there ] + world
}

