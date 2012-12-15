@Filter("intersect")
intersectBed = {
  exec "cat $input.bed $input.peak > $output"
}

run {
  "%_" * [ intersectBed ]
}
