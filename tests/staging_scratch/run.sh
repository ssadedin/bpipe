source ../testsupport.sh

rm -rf scratch_area test.sorted.txt test.count.txt .bpipe
mkdir scratch_area

printf "c\nb\na\n" > test.txt
echo "index" > test.txt.idx

run test.txt

grep -q "Pipeline Succeeded" test.out || err "Pipeline did not succeed"

exists test.sorted.txt test.count.txt

[ "$(cat test.sorted.txt | tr '\n' ' ')" == "a b c " ] || err "Sorted output has unexpected content: $(cat test.sorted.txt)"

[ "$(cat test.count.txt)" == "3" ] || err "Count output has unexpected content: $(cat test.count.txt)"

grep -q "STAGED INPUT: $PWD/scratch_area/bpipe-.*/test.txt" test.out || err "Staged command did not read input from the scratch area"

grep "STAGED LISTING:" test.out | grep -q "test.txt.idx" || err "Companion index file was not staged next to the input"

grep "UNSTAGED INPUT:" test.out | grep -q "scratch_area" && err "Unstaged command should not read from the scratch area"

[ -z "$(ls -A scratch_area)" ] || err "Scratch area was not cleaned up: $(ls -A scratch_area)"

# Re-running should find everything up to date
run test.txt

grep -q "STAGED INPUT" test.out && err "Staged stage executed again when its outputs were up to date"
grep -q "UNSTAGED INPUT" test.out && err "Unstaged stage executed again when its outputs were up to date"

true
