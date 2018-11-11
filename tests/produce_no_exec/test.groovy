hello = {
    produce("foo.txt") {
        new File(output.txt).text = "hey"
    }
}

run { hello }
