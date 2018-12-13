
answer=[ value: true ]

hello = {
    check {
        exec "$answer.value"
    } otherwise {
        println "The check failed"
    }
}

world = {
    answer.value = false
}

run {
    hello + world + hello
}
