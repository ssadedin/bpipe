// If a file that already exists or that is produced by a previous stage
// Is declared or inferred again as an output, we DON'T want to resave the meta data
// The main reason for this is that it confuses the dependency tree and creates
// apparent circular dependencies that foul up later logic
hello = {
  exec "cp $input $output ; echo cat >> $output"
}

there = {
  produce([input, 'test.txt.hello.there']) {
    msg "Output is $output2"
    exec "cp $input $output2; echo dog >> $output2"
  }
}

world = {
  exec "cp $input $output"
}

run { hello + there + world }

