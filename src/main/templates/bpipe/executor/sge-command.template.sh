#!/bin/sh
#\$ -wd ${bpipe.Runner.canonicalRunDirectory}
#\$ -N "$name"
#\$ -terse
#\$ -o $jobDir/$CMD_OUT_FILENAME
#\$ -e $jobDir/$CMD_ERR_FILENAME
#\$ -notify
<%if(config?.walltime) {%>#\$ -l h_rt=${config.walltime} <%}%>
<%if(config?.sge_pe && config?.procs) {%>
#\$ -pe ${config.sge_pe} ${config.procs} <%}
if(config?.procs && config.procs.toString().isInteger() && !config.containsKey("sge_pe") && config.procs.toString().toInteger()>1 ) { %>
#\$ -l slots=${config.procs} <%}
if(config?.memory) {%>
#\$ -l virtual_free=${config.memory}
<%}%>

trap 'echo 255 > $jobDir/$CMD_EXIT_FILENAME;' HUP INT TERM QUIT KILL STOP USR1 USR2

(
<% if(shell){%>${shell.join(' ')}<%}else{%>bash -e <%}%> ${CMD_FILENAME.absolutePath}
)

result=\$?
echo -n \$result > $jobDir/$CMD_EXIT_FILENAME

<%if(config?.post_cmd) {%>
${config.post_cmd}
<%}%>

exit \$result
