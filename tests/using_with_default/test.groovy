world = {
  requires x : "The x factor",
           y : "Why oh why? Yes, y!"

  exec """
    echo "x = $x and y = $y"
  """
}

hello = {
  var x : 10,
      y : 20

  exec """
    echo "x = $x and y = $y"
  """
}

run { hello.using(x:15) + world.using(x:"coolness") }
