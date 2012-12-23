bg = {
  exec """
    sleep 5 &

    sleep 6 &

    wait
  """
}

run { bg }
