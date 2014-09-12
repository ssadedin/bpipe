// A reference to an input with double extension should resolve
// only inputs that match the double extension
hello = {
    from("*.foo.csv") produce("hello.xml") {
        exec """
            touch ${output('bad.csv')}

            echo "Using $inputs.foo.csv"; touch $output.xml
        """
    }
}

run { hello }
