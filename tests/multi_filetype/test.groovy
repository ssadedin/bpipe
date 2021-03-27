
hello = {
    transform(~'(.*)(.csv|.tsv)') to('$1.xlsx') {
        exec """
            cp -v $input $output.xlsx
        """
    }
}

run { hello }
