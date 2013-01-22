hello = {
    from(["txt", "txt", "csv"]) {
        msg "inputs:  $input1 $input2 $input3"
    }
}

run { hello }
