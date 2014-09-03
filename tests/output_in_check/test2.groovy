hello = {
    exec """
        touch $output;
    """

    check {
        exec "echo 'Executing check ...'; [ -s $output ]"
    } otherwise {
        succeed "The output file had zero length"
    }
}
run { hello }
