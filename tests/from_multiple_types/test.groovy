hello = {
    from(["txt", "txt", "csv"]) {
        println "inputs:  $input1 $input2 $input3"
    }
}

run { hello }
