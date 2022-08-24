source ../testsupport.sh

run test.txt

grep -q 'WARNING: 1 check.s. failed' test.out || err "Check fail warning not printed"


bpipe checks -l > check_command.txt

grep -q ' world.*Failed' check_command.txt || err "Bpipe checks command did not show failed check"


bpipe retry > test2.out

grep -q 'WARNING: 1 check.s. failed' test2.out || err "Check fail warning not printed after retry"

bpipe checks -l > check_command2.txt

grep -q ' world.*Failed' check_command2.txt || err "Bpipe checks command did not show failed check after retry"

