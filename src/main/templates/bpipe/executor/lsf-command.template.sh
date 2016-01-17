#!/bin/sh
<%if(memoryMB) {%>
#BSUB -R "rusage[mem=$memoryMB]"
<%}%>
trap 'echo 255 > $jobDir/$CMD_EXIT_FILENAME;' HUP INT TERM QUIT KILL STOP USR1 USR2

(
bash -e ${CMD_FILENAME.absolutePath}
) > $jobDir/$CMD_OUT_FILENAME

result=\$?
echo -n \$result > $jobDir/$CMD_EXIT_FILENAME
exit \$result
