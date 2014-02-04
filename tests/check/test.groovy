hello = {
  check {
    exec "false"
  } otherwise {
    println "It failed"
  }
}

run { hello }
