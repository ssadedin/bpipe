
load 'foo.groovy'

if(FOO==1) {
    hello = {
        println "The correct value of FOO was used"
    }
}
else {
    hello = {
        println "The wrong value of FOO was used"
    }
}

run { hello }

