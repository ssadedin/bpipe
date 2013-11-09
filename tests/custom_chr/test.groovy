hello = {
  exec "echo $branch"
}

parallel_paths = ["mars","world","jupiter"] as Set

run {
  parallel_paths * [ hello ]
}

