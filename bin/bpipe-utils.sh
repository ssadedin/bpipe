#----------------------------------------------------------
# Kill a whole tree of processes in a cross platform way.
# Note, in some situations killing the parent process is 
# sufficient ot kill the children, however this doesn't
# happen universally, so this function actually recursively
# calls itself to iterate all child processes and kill them
#----------------------------------------------------------
killtree() {
    local ppid=$1
    
    if [ "$ppid" == "-1" ];
    then
        return
    fi
    
    # Sadly Mac OS/X does not seem to support --ppid option in default version
    # of ps
    if uname | grep -q Darwin;
    then
        pids=`ps -o pid,ppid | grep '^[0-9]' | grep ' '$ppid | cut -f 1 -d ' '`
    elif uname | grep -iq cygwin;
    then
        pids=`ps -f  | awk '{ if(NR>1) print  $2 " " $3 }' | grep ' '$ppid | cut -f 1 -d ' '`
    else
        pids=$(ps -o pid --no-headers --ppid ${ppid})
    fi
    
    if [ ! -z "$pids" ];
    then
        for child_pid in ${pids}; 
        do
            killtree ${child_pid}
        done
    fi
    
    # If second arg supplied, wait before killing parent
    # this allows Bpipe itself to notice its children are dead
    # by itself.
    if [ ! -z "$2" ];
    then
        sleep $2
    fi
    
    kill -TERM ${ppid} > /dev/null 2>&1
}


