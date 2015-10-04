
answer=true

hello = {
    check {
        exec "$answer"
    } otherwise {
        println "The check failed"
    }
}

world = {
    answer = false
}

run {
    hello + world + hello
}
