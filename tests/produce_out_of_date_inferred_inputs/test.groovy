hello = {
    exec "cp $input.txt $output.csv ; sleep 1"
}

world = {
    produce("qcstats.xml") {
        exec "cat $inputs.xml > $output.xml"
    }
}

run { hello + world }
