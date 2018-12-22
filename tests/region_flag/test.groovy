hello = {
    exec """
        echo "My region will be ${region.bedFlag('-L')}"
    """
}

run { 
    hello
}
