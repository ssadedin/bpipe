source ../testsupport.sh

run 

grep -q 'WARNING: 1 check.*failed' test.out || err "Did not observe 1 check fail as expected"

bpipe checks -l | grep -q '[0-9]. hello.*Passed' || err "One check did not pass"

bpipe checks -l | grep -q '[0-9]. hello.*Failed' || err "One check did not fail"

true
