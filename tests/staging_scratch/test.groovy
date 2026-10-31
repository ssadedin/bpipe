/*
 * Inputs of sort_lines are staged to a scratch directory (see bpipe.config), its
 * output is returned from scratch. count_lines is not staged.
 */
sort_lines = {
    transform('.txt') to('.sorted.txt') {
        exec """
            echo "STAGED INPUT: $input.txt"

            echo "STAGED LISTING: \$(ls \$(dirname $input.txt) | tr '\\n' ' ')"

            sort $input.txt > $output.txt
        """
    }
}

count_lines = {
    transform('.sorted.txt') to('.count.txt') {
        exec """
            echo "UNSTAGED INPUT: $input.txt"

            wc -l < $input.txt > $output.txt
        """
    }
}

run {
    sort_lines + count_lines
}
