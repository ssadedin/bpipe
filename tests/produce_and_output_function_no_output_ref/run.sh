source ../testsupport.sh

run

exists hello.txt world.txt

rm hello.txt

run

exists hello.txt

run

grep -q Running test.out && err "Command ran even though all outputs existed"

true
