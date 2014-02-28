//
// This test is run with the -d outdir flag 
// so all outputs (and subsequent inputs) should be in the outdir
//
hello = {
    produce("hello.output.csv") {
        exec "cp $input.txt $output.csv"
    }
}

world = {
    exec "cp $input.csv $output.xml"
}

run { hello + world }
