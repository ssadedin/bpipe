hello = {
    load branch.name + '.groovy'
    println "Hello $FOO"
    frobble()
}

world = {
    println "Hi there $FOO"
    frobble()
}

run {
    ['a','b'] * [hello + world]
}
