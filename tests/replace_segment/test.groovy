
hello = {
    println "Hello"

    if(branch.name == "jupiter") {
        branch.mars = jupiter_seg
    }
}

mars = {
    println "mars"
}

stupid = {
    println "stupid"
}

jupiter = {
    println "jupiter"
}

jupiter_seg = segment {
    stupid + jupiter
}

run { 
    ["mars","jupiter"] *  [ hello + mars ]
}
