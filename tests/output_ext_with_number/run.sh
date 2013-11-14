source ../testsupport.sh

run test.txt

exists test.hello.csv

# Note: used to be test.hello.world.csv
# However the refernece to $output2.csv without $output1 referenced is difficult to handle.
# we used to default to naming $output2 the same as $output1 if $output1 was not referenced
# anywhere. However this is tricky because we might see the references out of order - 
# $output2 first and then $output1. We can't know at the time $output2 is referenced whether
# $output1 is going to be referenced after. So without some kind of 2-phase property resolution
# process we have to assume $output1 might get referenced. Thus we now change to pre-emptively
# name $output2 on the assumption that we might see $output1 later on - ie: it gets a 
# numeric offset inserted, if it would end up the same name as $output1.
exists test.hello.2.world.csv

true
