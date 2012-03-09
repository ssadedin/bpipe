external = {
  exec 'echo "This is an external stage"'
  exec "echo external > test.external.txt"
}

another = {
  msg "Another stage"
}

all_external = segment { external + another }


