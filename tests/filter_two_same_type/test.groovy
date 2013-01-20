hello = {
  filter("foo","foo") {
    exec """
      cp $input1 $output1

      cp $input2 $output2
    """
  }
}

run { hello }
