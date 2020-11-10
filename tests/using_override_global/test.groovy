
foo="bar"

hello = {
    // requires foo : "foo must be provided"

    println "Foo = $foo"
}

run {
    hello.using(foo:"frog")
}

