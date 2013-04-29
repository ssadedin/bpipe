hello = {
  produce("test1.csv", "test2.csv") {
    exec "touch $output2.csv"
  }
}

run { hello }
