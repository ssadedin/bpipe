hello = {
      exec "cp $input.txt $output.csv"
}

there = {
    exec "cp $input.csv $output.xml"
}

world = {
    filter("fubar") {
        exec "cp $input.txt $output"
    }
}

run { hello + there + world }
