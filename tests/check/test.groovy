hello = {
  check {
    exec "false"
  } otherwise {
    // send text { "The test failed" } to gmail 
    println "It failed"
  }
}

run { hello }
