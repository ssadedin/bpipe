// Check that using the array / list form of from works
hello = {
    from(["foo.txt","bar.txt"]) {
        exec "echo $input1 $input2 > $output"
    }
}

run { hello }
