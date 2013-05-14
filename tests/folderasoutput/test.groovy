folder = {
  produce("thefolder/*") {
    exec "echo execute1; mkdir thefolder; cp $input thefolder/output.csv"
  }
}

after = {
  exec "echo execute2; cp $input.csv $output.xml"
}

run { folder + after }
