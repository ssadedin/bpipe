source ../testsupport.sh

bpipe run -d foobar test.groovy > test.out 2>&1

exists foobar/hello.txt

grep -q  'The output directory is.*foobar$' test.out || err 'Incorrect output dir printed in test output: -d option not respected'

true

