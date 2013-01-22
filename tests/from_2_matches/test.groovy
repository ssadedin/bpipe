/*
 * Test that a from that matches 2 inputs sets them as input1 and input2
 */
hello = {
    from("txt","txt") {
            filter("hello","hello2") {
              msg "$input1 => $output1"
              msg "$input2 => $output2"
              exec "cp $input1 $output1"
              exec "cp $input2 $output2"
            }
    }
}

world = {
    from("txt","txt") {
    filter("world") {
      msg "$input => $output"
      exec "cp $input $output"
    }
    }
}

run {
    hello + world 
}
