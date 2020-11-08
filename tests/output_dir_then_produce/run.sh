source ../testsupport.sh

run 

grep -q "Pipeline Succeeded" test.out || err "Pipeline producing output based on output.dir failed"

exists "verify_stuff/verify_stuff.log" 

notexists "verify_stuff.log" 

true
