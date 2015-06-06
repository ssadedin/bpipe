hello = {
    exec "echo $branch.parent / $branch.name ; sleep 1"
}

world = {

}

names = (97..110).collect { Character.toString((char)it) }

ages = (20..28)

run {
    names * [
        ages * [
            hello + world
        ]
    ]
}
