
/**
 * This filter is complex because at the time it is executed, the input
 * is the first input (test.txt), but then the second input is actually 
 * used (test.xml). Here we test that the expected filter output file 
 * is correctly updated to reflect the input that was really used.
 */
hello = {
    filter("slop") {
        exec """
            cp -v $input.xml $output.xml
        """
    }
}

run {
    hello 
}
