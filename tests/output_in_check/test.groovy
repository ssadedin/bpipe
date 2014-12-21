hello = {
    exec """
        cp $input $output; echo "Hello World" > $output
    """

    check {
        exec "echo 'Executing check ...'; [ -s $output ]"
    } otherwise {
        succeed "The output file had zero length"
    }
}
run { hello }
