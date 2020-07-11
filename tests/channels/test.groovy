foo = {
    exec "echo 'hello $branch/$stageName'"
}

bar = {
    produce "bar.${sample}.txt",  {
        exec "for i in {1..3}; do sleep 1; echo $i; done; echo 'hello $branch/$stageName' | tee $output.txt"
    }
}

bongo = {
    exec "echo 'hello $branch/$stageName'"
}

bingo = {
    exec "echo 'hello $branch/$stageName'"
}

cat = {
    branch.sample = branch.name

    exec "echo 'hello $branch/$stageName'"
}

tree = {
    exec "echo 'hello $branch/$stageName'"
}

house = {
    from('*.txt') {
        exec """
            echo "hello $input.txt -> $branch/$stageName" | tee $output.txt
        """
    }
}

samples = ['sample1','sample2']

run {
    samples * [ 
      align: cat + [
        bongo + bingo, 
        foo + bar >> 'qc'
      ],
      qc: tree + house
    ]
}
