hello = {
    println "Hello"
    branch.fubar = true
}

world = {
    println "world"
}

mars = {
    println "a red planet"
}

var FOO : true,
    BAR : false

run {
    hello.when { FOO } + world.when { BAR } + mars.when { fubar }
}
