source ../testsupport.sh

# fail inside a stage: message is reported with the stage name
bpipe run test.groovy > test.out 2>&1

grep -q "Pipeline stage hello aborted with the following message" test.out || err "Failure inside stage did not report stage name"
grep -q "I failed at the first step" test.out || err "Failure inside stage did not report user message"
grep -q "Pipeline Failed" test.out || err "Pipeline did not fail after fail inside stage"

# fail at the top level of the script, before the pipeline runs
rm -rf .bpipe
bpipe run -p FAIL_BEFORE_RUN=true test.groovy > test.out 2>&1

grep -q "I failed before execution" test.out || err "Top level fail did not report user message"
grep -q "No signature of method" test.out && err "Top level fail was not recognised"
grep -q "Stage hello" test.out && err "Stage executed after top level fail"

# fail inside a named branch: message includes the branch name
rm -rf .bpipe
bpipe run branch.groovy > test.out 2>&1

grep -q "Pipeline stage hello in branch s1 aborted" test.out || err "Failure in branch s1 did not report branch name"
grep -q "Pipeline stage hello in branch s2 aborted" test.out || err "Failure in branch s2 did not report branch name"

true
