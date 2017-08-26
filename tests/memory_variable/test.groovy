
hello = {
    exec """
        printf "Branch $branch will use ${memory}gb of memory: "

        sleep 3

        printf "Done"

    """, "hello"
}

world = {
    exec """
        echo "I will use ${memory}gb of memory"
    ""","world"
}

run {
    [1,2,3,4] *  [ hello ] + world
}
