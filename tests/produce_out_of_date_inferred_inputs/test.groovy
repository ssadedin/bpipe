hello = {
    exec "echo executing1; cp $input.txt $output.csv ; sleep 2"
}

world = {
    produce("qcstats.xml") {
        exec "echo executing2; cat $input.xml > $output.xml"
    }
}

run { hello + world }
