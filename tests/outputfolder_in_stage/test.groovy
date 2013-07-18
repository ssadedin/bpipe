
hello = {
  println "The output folder is $output.dir"
  exec "echo hello > $output"
}

world = {
  output.dir = "world_dir"

  transform("txt") {
    exec "echo world > $output.txt"
  }

  output.dir = "mars_dir"
  transform("txt") {
    exec "echo mars > $output.txt"
  }
}

run { hello + world }
