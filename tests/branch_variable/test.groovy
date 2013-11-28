
hello = {
    branch.foo = "bar"
    exec "cp $input.txt $output.csv"
}

world = {
    println "branch.foo = $branch.foo"
    exec "echo $branch.foo | cat - $input.csv > $output.tsv"
}

there = {
    println "branch.foo = $branch.foo"
    exec "echo $branch.foo | cat - $input.tsv > $output.xml"
}

greetings = {
    // Both forms should resolve
    println "branch.foo = $branch.foo"
    println "foo = $foo"
    exec "echo $foo | cat - $input.xsl > $output.bed"
}

run {
    hello + world + [ there ] + 'test_%.xsl' * [ greetings ]
}
