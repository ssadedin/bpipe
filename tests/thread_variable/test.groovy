hello = {

  uses(tempfiles: 1024) {

    multi "echo $threads > $output",
          "echo $threads > $output2",
          "echo $threads > $output3"

    exec "echo $threads > $output4"

  }
}

run { hello }
