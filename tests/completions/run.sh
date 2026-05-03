source ../testsupport.sh

# Test 1: bpipe completions prints a non-empty completion script
OUTPUT=$(bpipe completions)
[ -n "$OUTPUT" ] || err "bpipe completions produced no output"
echo "$OUTPUT" | grep -q "_bpipe_complete" || err "Completion script missing _bpipe_complete function"
echo "$OUTPUT" | grep -q "complete -F _bpipe_complete bpipe" || err "Completion script missing complete registration"

# Source the completion script for remaining tests
eval "$(bpipe completions)"

# Helper to simulate completion
test_complete() {
    local line="$1"
    COMP_LINE="$line"
    COMP_POINT=${#line}
    # Parse words including trailing empty word if line ends with space
    if [[ "$line" == *" " ]]; then
        # Read words from line (excluding trailing space), then add empty word
        local words=($line)
        COMP_WORDS=("${words[@]}" "")
        COMP_CWORD=${#COMP_WORDS[@]}
        ((COMP_CWORD--))
    else
        COMP_WORDS=($line)
        COMP_CWORD=$((${#COMP_WORDS[@]} - 1))
    fi
    _bpipe_complete
}

# Test 2: top-level commands
test_complete "bpipe "
echo "Top-level completions: ${COMPREPLY[*]}" |grep -q "run" || err "Missing 'run' in top-level completions"
echo "Top-level completions: ${COMPREPLY[*]}" |grep -q "stop" || err "Missing 'stop' in top-level completions"
echo "Top-level completions: ${COMPREPLY[*]}" |grep -q "completions" || err "Missing 'completions' in top-level completions"

# Test 3: run options (user needs to type '-' to see options)
test_complete "bpipe run -"
echo "Run options: ${COMPREPLY[*]}" |grep -q "\-t" || err "Missing '-t' in run completions"
echo "Run options: ${COMPREPLY[*]}" |grep -q "\-d" || err "Missing '-d' in run completions"
echo "Run options: ${COMPREPLY[*]}" |grep -q "\-n" || err "Missing '-n' in run completions"

# Test 4: test command also offers run options
test_complete "bpipe test -"
echo "Test options: ${COMPREPLY[*]}" |grep -q "\-t" || err "Missing '-t' in test completions"

# Test 5: cleanup options
test_complete "bpipe cleanup -"
echo "Cleanup options: ${COMPREPLY[*]}" |grep -q "\-y" || err "Missing '-y' in cleanup completions"

# Test 6: groovy file and directory completion for run-like commands
TMPDIR=$(mktemp -d)
cd "$TMPDIR"
touch a.groovy b.groovy c.txt
mkdir -p subdir

# Empty cur -> dirs and .groovy files
COMPREPLY=()
test_complete "bpipe run "
echo "Run files: ${COMPREPLY[*]}" |grep -q "a.groovy" || err "Missing 'a.groovy' in run file completions"
echo "Run files: ${COMPREPLY[*]}" |grep -q "subdir/" || err "Missing 'subdir/' in run dir completions"
echo "Run files: ${COMPREPLY[*]}" |grep -qv "c.txt" || err "Should not include 'c.txt' in run file completions"

# Partial file match
COMPREPLY=()
test_complete "bpipe run a"
[ ${#COMPREPLY[@]} -eq 1 ] || err "Expected exactly 1 completion for 'a', got ${#COMPREPLY[@]}"
[ "${COMPREPLY[0]}" == "a.groovy" ] || err "Expected a.groovy for partial match, got ${COMPREPLY[0]}"

# Partial dir match
COMPREPLY=()
test_complete "bpipe run s"
[ ${#COMPREPLY[@]} -eq 1 ] || err "Expected exactly 1 completion for 's', got ${#COMPREPLY[@]}"
[ "${COMPREPLY[0]}" == "subdir/" ] || err "Expected subdir/ for partial match, got ${COMPREPLY[0]}"

# Other run-like commands also complete files
test_complete "bpipe test "
echo "Test files: ${COMPREPLY[*]}" |grep -q "a.groovy" || err "Missing 'a.groovy' in test file completions"

cd - >/dev/null
rm -rf "$TMPDIR"

true
