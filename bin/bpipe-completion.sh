#!/bin/bash
# Bpipe Bash Completion Script
#
# To install, add the following to your ~/.bashrc or ~/.bash_profile:
#
#   eval "$(bpipe completions)"
#
# Or save to a file and source it:
#
#   bpipe completions > /etc/bash_completion.d/bpipe
#   source /etc/bash_completion.d/bpipe
#

_bpipe_complete() {
    local cur prev comp_words comp_cword

    comp_cword=${COMP_CWORD:-0}
    cur="${COMP_WORDS[$comp_cword]}"
    if [ "$comp_cword" -ge 1 ]; then
        prev="${COMP_WORDS[$comp_cword-1]}"
    else
        prev=""
    fi
    comp_words=("${COMP_WORDS[@]}")

    local commands=(
        run test debug touch execute retry remake resume stop history log jobs
        checks override status cleanup query preallocate archive autoarchive
        preserve register diagram diagrameditor console documentation
        cleancommands agent queue dev completions
    )

    local run_options=(
        -h --help
        -d --dir
        --delay
        -a --autoarchive
        -t --test
        -f --filename
        -r --report
        -R --report
        -n --threads
        -m --memory
        -l --resource
        -v --verbose
        -y --yes
        -u --until
        -p --param
        -b --branch
        -s --source
        -e --env
        -L --interval
    )

    local run_like_commands=(run test debug touch execute retry remake resume dev queue diagram)

    local word_count=${#comp_words[@]}

    if [ "$word_count" -le 2 ]; then
        COMPREPLY=($(compgen -W '${commands[*]}' -- "$cur"))
        return
    fi

    local cmd="${comp_words[1]}"

    if [ "$cmd" == "completions" ]; then
        return
    fi

    if [[ " ${run_like_commands[*]} " == *" $cmd "* ]]; then
        if [[ "$cur" == -* ]]; then
            COMPREPLY=($(compgen -W '${run_options[*]}' -- "$cur"))
        else
            local dirs=($(compgen -d -S "/" -- "$cur"))
            local files=($(compgen -f -X "!*.groovy" -- "$cur"))
            COMPREPLY=($(printf '%s\n' "${dirs[@]}" "${files[@]}"))
        fi
        return
    fi

    if [ "$cmd" == "cleanup" ]; then
        if [[ "$cur" == -* ]]; then
            COMPREPLY=($(compgen -W '-y --yes' -- "$cur"))
        fi
        return
    fi

    if [ "$cmd" == "archive" ] || [ "$cmd" == "autoarchive" ]; then
        if [[ "$cur" == -* ]]; then
            COMPREPLY=($(compgen -W '--delete' -- "$cur"))
        fi
        return
    fi

    if [ "$cmd" == "log" ]; then
        if [[ "$cur" == -* ]]; then
            COMPREPLY=($(compgen -W '-n' -- "$cur"))
        fi
        return
    fi

    if [ "$cmd" == "stop" ]; then
        COMPREPLY=($(compgen -W 'preallocated' -- "$cur"))
        return
    fi
}

complete -F _bpipe_complete bpipe
