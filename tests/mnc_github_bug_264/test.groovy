convert = {
  transform('.mnc') to('.convert.mnc') {
    exec """
        cp -v  $input.mnc $output.mnc
    """
  }
}

run { 
    convert
}
