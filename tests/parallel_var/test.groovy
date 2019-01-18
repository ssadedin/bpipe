counts = Collections.synchronizedMap([:])

hello = {
    var foo : branch.name

    counts[foo] = counts.get(foo,0) + 1

    println "My foo is $foo"
}

run {

    (1..100) * [ hello.using(fubar:5) ]

    // (1..1000) * [ hello ]
}

println "counts > 1: " + counts.count { it.value > 1 }

// println bpipe.Utils.table(['foo','value'], [ counts*.key, counts*.value].transpose())
