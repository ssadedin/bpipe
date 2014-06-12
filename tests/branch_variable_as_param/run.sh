source ../testsupport.sh

run

exists hello.bar.txt hello.foo.txt
notexists hello.txt

