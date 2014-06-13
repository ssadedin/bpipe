
hello = {
    transform("xml","csv") {
            exec """touch $output2; false"""

            exec "touch $output1"
    }
}

run { hello }

