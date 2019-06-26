hello = {
    new Thread({
      Thread.sleep(420)
      println "Create file foo.txt"
      new File('foo.txt').text = 'hello'
    }).start()

    assert file('foo.txt').text == 'hello'
}

run {
    hello
}
