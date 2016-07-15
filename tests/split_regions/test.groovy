// genome 'hg19'

hello = {
    println "Executing for $region on $region.bed"
}

myRegions = new bpipe.RegionSet([ 
        new bpipe.Sequence(name:"chr1", range:0..1000), 
        new bpipe.Sequence(name:"chr2", range:0..500)
    ]
)


run {
    myRegions.split(3) * [ hello ]
}

