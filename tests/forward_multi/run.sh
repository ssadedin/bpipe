source ../testsupport.sh

run myinput.txt

exists myinput.txt.testforward.echoAgain

grep -q "This is the end: myinput.txt.testforward.echoAgain" test.out || err "Failed to find correct output echoed in message"

true
