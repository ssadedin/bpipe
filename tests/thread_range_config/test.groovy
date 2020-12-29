hello = {
    exec """
        echo "I am using $threads threads"
    """, "hello"

}

run {
    hello
}
