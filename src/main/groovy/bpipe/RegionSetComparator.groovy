package bpipe

import groovy.transform.CompileStatic

@CompileStatic
class RegionSetComparator implements Comparator {

    @Override
    public int compare(Object oa, Object ob) {

        RegionSet a = (RegionSet) oa
        RegionSet b = (RegionSet) ob

        // Return the smallest region
        long aSize = a.size()
        long bSize = b.size()
        int result = bSize <=> aSize
        if(result)
            return result

        // Return region with smallest sequence
        if(aSize == 0) // bSize must also be 0
            return 0
        
        // Sizes are the same - compare the internal sequences
        Iterator<Map.Entry<String,Sequence>> aIter = a.sequences.iterator()
        Iterator<Map.Entry<String,Sequence>> bIter = b.sequences.iterator()
        while(aIter.hasNext() && bIter.hasNext()) {
            Sequence aSeq = aIter.next().value
            Sequence bSeq = bIter.next().value
            
            result = bSeq.name <=> aSeq.name
            if(result)
                return result
            
            result = aSeq.range.from <=> bSeq.range.from
            if(result)
                return result
                
            result = aSeq.range.to <=> bSeq.range.to
            if(result)
                return result
             
            if(!aIter.hasNext() && bIter.hasNext())
                return -1
            else
            if(!bIter.hasNext() && aIter.hasNext())
                return 1
        }
        return 0
    }
}
