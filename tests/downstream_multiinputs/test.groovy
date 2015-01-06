hello = {
    exec "cat $input1.csv $input2.csv > $output.xml"
}

world = {
    exec "cat $input3.csv $input4.csv > $output.xml"
}

run { hello + world }
