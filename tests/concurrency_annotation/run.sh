source ../testsupport.sh

../../bin/bpipe run -n 5 test.groovy > test.out 2>&1

for i in one two three four five;
do
    grep -q "^start $i" times.txt || err "Failed to find start of stage instance $i"
    grep -q "^end $i" times.txt || err "Failed to find end of stage instance $i"
done

# Sorting the start / end times of the five stage instances lets us count the
# greatest number of instances that were running at the same time. The stage is
# annotated with @concurrency(2), so that number must not exceed two. And since
# there are five branches run at a global concurrency of five, we do expect it
# to reach the maximum the annotation allows.
MAX=`sort -k3 -n times.txt | awk '{ if ($1 == "start") { count++; if(count>max) max=count } else { count-- } } END { print max+0 }'`

[ "$MAX" -gt 2 ] && err "Found $MAX instances of the stage running at once, but the @concurrency(2) annotation should limit it to 2"

[ "$MAX" -lt 2 ] && err "Found only $MAX instances of the stage running at once: expected five branches to run two at a time"

true
