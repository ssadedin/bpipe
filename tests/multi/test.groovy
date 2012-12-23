async_stuff = {
  multi "sleep 5; echo foo > foo.txt",
        "sleep 7; echo bar > bar.txt; false"
}

run { async_stuff }
