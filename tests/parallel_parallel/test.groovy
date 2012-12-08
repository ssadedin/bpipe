
hello = {
  exec "cp $input $output"
}

there = {
  exec "cp $input $output.xls"
}

help = {
  exec "cp $input $output"
}

me = {
  exec "cp $input $output.xml"
}

world = {
  exec "ls $inputs > result.out"
}

run {
  "*" * [ 
    "%.csv" * [ hello + there ],
    "%.txt" * [ help + me ]
  ] + world
}

