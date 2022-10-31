hello = {
    exec """
       echo "I will use $threads threads"
    """, "hi"
}

run {
   hello
}

