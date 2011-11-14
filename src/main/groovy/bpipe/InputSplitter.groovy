package bpipe

import java.util.logging.Logger;
import java.util.regex.Matcher;

/**
 * Implements logic for splitting input files into groups by sample using simple
 * wildcard patterns.  The goal is to provide a simpler way for people to specify
 * how file names are related to sample names than asking people to either do it 
 * manually or to write complicated regexs or other logic.
 * <p>
 * The logic used here is based on simple file globbing (using the * operator) but with
 * a special % wildcard operator that indicates the portion of the file name that 
 * represents the sample name.  The user can specify a mask that matches their 
 * file names such as sequence_%_*.txt and the input files will be grouped into those 
 * that have the same digit in the % position, then ordered based on the matches to
 * the wild card position.  
 * <p>
 * The result is that the user can just shove a whole directory files in (even ones
 * that do not match their "mask" at all and they will be filtered to the set that
 * matches the mask and then split into groups based on sample, each group of which can 
 * then be individually handled separately by the pipeline.
 * 
 * @author simon.sadedin@mcri.edu.au
 */
class InputSplitter {

    /**
     * Logger to use with this class
     */
    private static Logger log = Logger.getLogger("bpipe.InputSplitter");
	
    /**
     * Splits the given inputs up according to the specified pattern 
     * where a splitting point is indicated by a % character and a 
     * grouping and ordering point is indicated by a * character.  Thus all 
     * inputs with the differing section matched by % are split into separate groups 
     * while all those with matching sections covered by * are grouped and ordered
     * numerically if they contain leading numbers, otherwise they are ordered
     * lexically.
     * 
     * @param pattern
     * @param input
     * @return
     */
	Map split(String pattern, input) {
		// Replace the two special characters (*) and (%) with 
		// regex groups
        List groups = this.convertPattern(pattern)
        String regex = groups[0]
        int splitGroup = groups[1]
        
        def unsortedResult = [:]
        for(String inp in Utils.box(input)) {
            Matcher m = (inp =~ regex)
            if(!m)
			    continue
                
            String group = m[0][splitGroup+1]
            log.fine "The group:  $group"
            if(!unsortedResult.containsKey(group))
                unsortedResult[group] = []
                
            unsortedResult[group] << inp
		}
        
		// We now have all the inputs keyed on the part matching the split char, 
		// however we want to also sort them 
        Map sortedResult = [:]
        unsortedResult.each {  k,v ->
            sortedResult[k] = this.sortNumericThenLexically(regex, splitGroup, v)
		}
	}
    
    /**
     * Sort by using the groups matched by the given regex
     * first numerically and then lexically while skipping
     * the given group.
     * <p>
     * NB: this is horrifically inefficient.  It rematches the same
     * regexes with every sort comparison.  If it ever matters
     * it can be greatly optimized.
     * 
     * @param v
     * @return    a reordered version of the input list
     */
    List sortNumericThenLexically(String regex, int skipGroup, List v) {
		return v.sort { String i1, String i2 ->
            // Match 
			Matcher m1 = (i1 =~ regex)
			Matcher m2 = (i2 =~ regex)
            
            // Convert the regex matches to lists
            List g1 = [], g2 = []
            int count = 0
            for(m in m1[0]) {
                log.fine "==>  $m"
                if(count++ == skipGroup)
                    continue
			    g1 << m
            }
            count = 0
            for(m in m2[0]) {
                if(count++ == skipGroup)
                    continue
			    g2 << m
            }
            
			// Work through each group and return on the first difference
            log.fine "groups:  $g1  $g2"
			for(int i=0; i<g1.size(); ++i) {
                String s1=g1[i], s2=g2[i]
                
				if(s1 == s2)
				    continue
                
				log.fine "Compare:  $s1 : $s1"
                
                // Numeric 
				if(s1.matches("^[0-9].*") && s2.matches("^[0-9].*")) {
					Integer n1 = Integer.parseInt((s1 =~ "^([0-9])")[0][0] )
					Integer n2 = Integer.parseInt((s2 =~ "^([0-9])")[0][0] )
                    return n1.compareTo(n2)
				}
                else
    				return s1.compareTo(s2)
			}
            
			log.fine "$i1 == $i2"
            return 0
		}    }
    
    /**
     * Converts the given pattern from simplified Bpipe 
     * split format to a normal regular expression
     * format.
     * 
     * @return     a list containing two elements: the pattern generated 
     *             and an integer indicating the index of the capture group
     *             representing the split point.
     */
	List convertPattern(String pattern) {
        
		// Find the characters flanking the % and * and use those as
		// pattern delimiters
        int percPos = pattern.indexOf('%')
//        if(percPos == -1)
//		    throw new PipelineError("A sample split pattern must contain a % character to indicate the splitting point")
		
		def starMatch = (pattern =~ /\*/)
		List starPos = []
        for(s in starMatch) {
		    starPos << starMatch.start()   
		}
        
		log.fine "Found * pattern at $starPos and % at $percPos"
        
        List sorted = (starPos + percPos).sort()
		
        log.fine "Sorted: $sorted"
        
		int percGroupPos = sorted.indexOf(percPos)
        if(percPos < 0) {
		    sorted = starPos
            sorted.sort()
        }
        
		log.fine "% is group # " + percGroupPos
        
		// Figure out flanking characters.  Rather than making the user 
		// specify which characters delimit their expression we just use the 
		// characters on either side of the wild cards
        def result = new StringBuilder()
        def lastPos = -1
        def lastRight = ""
        for(c in sorted) {
			def leftFlank = new StringBuilder()
            int lpos = c
            while(lpos && (lastPos < 0 || (lpos-1 != lastPos+1))) {
                leftFlank.append(pattern[lpos-1]) 
                --lpos
            }
            leftFlank = leftFlank.toString().reverse()
           
            //			def leftFlank = c && (lastPos < 0 || (c-1 != lastPos+1)) ? pattern[c-1] : ""
            
			def rightFlank = c<pattern.size()-1 ? pattern[c+1].replaceAll(/\./,/\\./) : ""
            
            log.fine "Position $c : left=$leftFlank right=$rightFlank"
            
            // If there is no right hand flank then don't include
			// any exclusion in the pattern.  If there is one,
			// exclude characters matching the right flanking character
            def wildcard = rightFlank ? "[^"+rightFlank+"]*" : ".*" 
            def group = "($wildcard)"
            def between = ""
            log.fine "tween range = " + (lastPos+1+lastRight.size()..c)
            if(lastPos >= 0)
                between =  pattern.substring(lastPos+1+lastRight.size(), c)
            result << between + leftFlank + group + rightFlank
			log.fine "between for $c:  " + between
			log.fine "chunk for $c:  " + (leftFlank + group + rightFlank)
            lastPos = c
            lastRight = rightFlank
		}
        int lastRenderedPos = lastPos+1+lastRight.size()
        if(lastRenderedPos < pattern.size()-1)
            result << pattern.substring(lastRenderedPos-1, pattern.size())
		def resultList = [ result.toString() ,  percGroupPos]
        
	    log.fine "Result is $resultList"
        return resultList
	}
}
