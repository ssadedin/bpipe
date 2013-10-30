// Reported on Google Group: https://groups.google.com/forum/#!topic/bpipe-discuss/4BclM9_5BIU

hello = {
    exec "echo Hello"
}
world = {
    exec "echo World"
}
other = {
    exec "echo other"
}
more = {
    exec "echo more"
}
// run{ hello + more + other + world }
run { hello + [more,other] + world }
