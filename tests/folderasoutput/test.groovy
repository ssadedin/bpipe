folder = {
  produce("thefolder/*") {
    exec "mkdir thefolder; cp $input thefolder/output.csv"
  }
}

after = {
  exec "cp $input.csv $output.xml"
}

run { folder + after }
