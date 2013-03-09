source ../testsupport.sh

run test.txt

exists test.txt.hello

[ `cat test.txt.hello` == foo ] || err "Incorrect contents in output file test.txt.hello"

true
