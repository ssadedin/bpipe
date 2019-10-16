options {
    foo 'The foo to bar with', args: 1, required: true
    bar 'The bar to foo with', args: 1, required: true
}

hello = {
    exec """
        echo "Inputs are: $inputs"

        echo "$opts.foo => $opts.bar"

        cp -v $input.txt $output.txt
    """
}

run { hello }

