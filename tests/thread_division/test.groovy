well = {
    exec """ 
        echo well
        """
}

hello = {
    uses(threads:1..10) {
        exec """
            sleep 1 

            echo "hello using $threads"

            sleep 5
        """
    }
}

world = {
    exec """
        echo world

        sleep 7
    """
}

there = {
    exec """ 
        echo there
        """
}

hello2 = {
    uses(threads:1..2) {
        exec """
            echo "hello2 using $threads"

            sleep 5
        """
    }
}

world2 = {
    exec """
        echo world2

        sleep 3
    """
}

run {
    well + [ hello, world ] + there + [hello2, world2]
}
