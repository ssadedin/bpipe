hello = {
  exec "echo Hello World"
}

fail = {
  exec "false"
}

Bpipe.run {
  hello + fail
}
