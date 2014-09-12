hello = {
    produce(~"hello[0-9]_.*.xml") {
        exec """
            touch hello4_world.xml
             """
    }
}

run { hello }
