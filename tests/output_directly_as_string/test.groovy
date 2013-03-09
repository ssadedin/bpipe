hello = {
  def text = new File(input).text

  new File(output).text = text
}

run { hello }
