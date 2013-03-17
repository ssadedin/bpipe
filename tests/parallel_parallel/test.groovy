
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
  msg "ls $inputs.xls $inputs.xml > result.out"
  exec "ls $inputs.xls $inputs.xml > result.out"
}

run {
  "*" * [ 
    "%.csv" * [ hello + there ],
    "%.txt" * [ help + me ]
  ] + world
}

