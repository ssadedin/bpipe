source ../testsupport.sh

run test.txt

grep -q 'ERROR: Expected output file' test.out && err 'Wrong error message for failed check with missing output'

grep -q "Check 'the world is failing' in stage world failed, and also failed to create one or more required outputs" test.out \
    || err 'Expected error message not found for check failing with missing output'

bpipe run -p PRE_FAIL=false test.groovy test.txt > test.out 2>&1

grep -q 'Pipeline Succeeded' test.out || err "Did not find pipeline succeeded message after retry of failed check now passing"

grep -q 'WARNING: 1 check.*failed' test.out && err "Checks failed message shown even though checks now passed after re-execution"

bash cleanup.sh

bpipe run -p PRE_FAIL=false -p POST_FAIL=true test.groovy test.txt > test.out 2>&1

grep -q 'Pipeline Succeeded' test.out || err "Did not find pipeline succeeded message when only a check failed"

grep -q 'WARNING: 1 check.*failed' test.out || err "Checks failed message not shown when checks should have failed"


## Otherwise clause should not be executed when one of the outputs produced by the check
## failed to get created. The main reason is that these outputs are expected to be
## available in the otherwise clause, and if not the user gets cryptic errors about the
## outputs being missing that obscure the real failure (the check failing).
bash cleanup.sh 
bpipe run -p PRE_FAIL=true -p POST_FAIL=false -p WITH_OTHERWISE=true test.groovy test.txt > test.out 2>&1

grep -q 'ERROR: stage world failed:' test.out || err "Stage failed message not shown when check output not produced"

grep -q "Check 'the world is failing' in stage world failed" test.out || err "Expected message about check failing not shown"

grep -q "WARNING: 1 check(s) failed" test.out || err "Check failed message not shown when check check fails with output not produced"

bash cleanup.sh 
bpipe run -p PRE_FAIL=false -p POST_FAIL=true -p WITH_OTHERWISE=true test.groovy test.txt > test.out 2>&1

grep -q "WARNING: 1 check(s) failed" test.out || err "Check failed message not shown when check check fails with output produced"

grep -q "Otherwise I am fine" test.out || err "Otherwise clause not executed for check failure with output produced"

