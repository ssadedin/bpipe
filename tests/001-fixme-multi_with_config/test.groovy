hello = {

    multi config1: """
                echo "this is the first command using $threads threads"
            """,
          config2: """
                echo "another command using $threads threads"
            """

}

run { 
    hello 
}
