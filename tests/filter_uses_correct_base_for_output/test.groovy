

/*
 * Here there are two inputs used. If the 
 * output was referenced as $output then Bpipe woudl use the 
 * first of the inputs to determine the file name to base the output on.
 * However when the form $output.bed is used, Bpipe should select the 
 * input that matches the output extension as the base for the 
 * output file name.
 *
 * Eg: we would like 
 *
 *   somecommand foo.bed bar.txt > foo.xxx.bed
 *
 * not
 *
 *   somecommand foo.bed bar.txt > bar.xxx.bed
 *   
 */
hello = {
    filter("hello") {
        exec """
            cat $input.txt $input.bed > $output.bed
        """
    }
}

run { hello }
