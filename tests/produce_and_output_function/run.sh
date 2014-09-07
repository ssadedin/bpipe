source ../testsupport.sh

run

exists hello.txt world.txt

rm world.txt

run

exists hello.txt world.txt

