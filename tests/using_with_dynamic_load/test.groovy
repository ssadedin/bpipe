

hello = {
    load 'loaded.groovy'
}

world = {
    var HELLO : false


    println "HELLO = $HELLO"
}




run  {
    hello + world +  world.using(HELLO: true) 
}

