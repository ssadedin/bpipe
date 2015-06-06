hello = {
  check {
    exec "false"
  } otherwise {
    // send text { "The test failed" } to gmail 
    send text {"This horrible test failed, I don't know what to do, it really is a disaster"} to file: "test.txt"
    println "It failed"
  }
}

run { hello }
