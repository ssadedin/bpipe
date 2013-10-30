source ../testsupport.sh

run 

grep -q "World" test.out || err "Failed to find last stage (World) printed in output: something went wrong"

true

