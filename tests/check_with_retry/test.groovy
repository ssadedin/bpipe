
hello = {
    exec "cp -v $input.txt $output.csv"
}

world = {
  check {
      exec """
          echo "ok" > $output.tsv

          false
      """
  } otherwise {
      send text { "it is all " + file(output.tsv).text  } to file: 'check_output.txt'
  }
}

run {
    hello + world
}
