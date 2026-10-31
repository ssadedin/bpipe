source ../testsupport.sh

rm -rf scratch_area test.sorted.txt .bpipe
mkdir scratch_area

printf "c\nb\na\n" > test.txt

run test.txt

grep -q "Pipeline Failed" test.out || err "Pipeline did not fail"

notexists test.sorted.txt

[ -z "$(ls -A scratch_area)" ] || err "Scratch area was not cleaned up: $(ls -A scratch_area)"

true
