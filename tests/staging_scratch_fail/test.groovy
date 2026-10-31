/*
 * A staged command that writes its output and then fails: the output must not be
 * returned and the scratch area must be cleaned up.
 */
sort_lines = {
    transform('.txt') to('.sorted.txt') {
        exec """
            sort $input.txt > $output.txt

            exit 3
        """, "staged"
    }
}

run {
    sort_lines
}
