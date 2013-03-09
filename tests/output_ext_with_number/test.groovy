hello = {
  exec "cp $input $output1.csv"
}

world = {
  exec "cp $input $output2.csv"
}

run { hello + world }
