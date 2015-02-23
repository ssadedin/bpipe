hello = {
   println "Hello" 
}

there = {
   println "There"
   branch.parent.foo = "Cheese"
}

world = {
   println "World : $foo"
}

run {
    
    hello  + [ there ] + world
}
