
foo="bar"

hello = {
    // requires foo : "foo must be provided"

    println "Foo = $foo, grokkle=$grokkle"
}

run {
    hello.using(foo:"frog")
}

