config {
    executor="torque"
    memory="3g"

    commands {
        hello {
            memory="5g"
        }
    }
}

hello = {
    exec """
        echo 'hello'
    """, "hello"
}

world = {
    exec """
        echo 'world'
    """
}

run {
    hello + world
}


