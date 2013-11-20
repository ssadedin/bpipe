source ../testsupport.sh

run test.txt

exists test.bar.hello.csv test.foo.hello.csv test.tree.hello.csv

true
