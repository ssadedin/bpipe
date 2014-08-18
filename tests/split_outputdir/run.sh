source ../testsupport.sh

run foo/test.txt bar/test.txt

exists test.foo.hello.csv test.bar.hello.csv

true
