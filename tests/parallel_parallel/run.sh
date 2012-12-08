source ../testsupport.sh

run *.txt *.csv

exists "result.out" 
exists test1.csv.there.xls test2.csv.there.xls
exists test1.txt.me.xml test2.txt.me.xml

wc result.out | grep -q "4[^0-9]*4" || err "Failed to find correct number of files in result.out"

for i in test1.csv.there.xls test2.csv.there.xls test1.txt.me.xml test2.txt.me.xml;
do
  grep -q $i result.out || err "File $i not found as input forwarded from parallel stages as expected"
done


