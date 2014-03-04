hello = {
    requires planet : "The  planet to greet"

    exec """
        echo "hello $planet"
    """
}

run { hello.using(planet: "mars") }
