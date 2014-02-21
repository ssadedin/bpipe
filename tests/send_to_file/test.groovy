hello = {
    
    var planet : "mars"

    send report('template.txt') to file: 'test.output.txt', from: 'Earth'
}

run { hello }
