hello = {
  exec "cp $input.txt $output.csv"
}

@filter("wow")
world = {
  exec "cp $input.xml $output"
}

run { hello + world }
