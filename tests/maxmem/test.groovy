hello = {
    uses(GB: 5) {
        exec """
            echo Hello; sleep 5; echo World
        """
    }
}

run { 
    ["foo","bar"] * [ hello ]
}
