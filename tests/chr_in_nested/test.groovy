
do_foo = {
  transform('.foo') {
    exec "echo doing $chr foo from $input.baz; touch $output.foo"
  }
}

do_bar = {
  transform('.bar') {
    exec "echo doing $chr bar from $input.baz; touch $output.bar"
  }
}

end = {
    exec "echo end"
}

run {
     chr(1..1) * [ [ do_foo ] + end ] 
}
