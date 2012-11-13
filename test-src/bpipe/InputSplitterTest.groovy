package bpipe;

import static org.junit.Assert.*;

import org.junit.Test;

class InputSplitterTest {

	InputSplitter splitter = new InputSplitter()
    
    
    /**
     * A real example from bpipe user Rory
     */
    @Test
    void testMixedEndings() {
        def inputs = [
                      "test.chr22.csv",
                      "test.chr22.realign.bam"
                     ]
        
        def pattern = ".chr%."
        def regex = splitter.convertPattern(pattern)
        println "regex = $regex"
        def result = splitter.split(pattern,inputs)
        
        println "Got result:  $result"
        
        assert result == [
          "22": ["test.chr22.csv","test.chr22.realign.bam"]
        ]
    }
 
//    
//	@Test
//	public void testSplit() {
//        
//		def inputs = ["s_1_1.txt", "s_1_2.txt", "s_2_2.txt", "s_2_1.txt", "foo.bar"]
//        def result = splitter.split("s_%_*.txt",inputs) 
//        assert result == [ 
//		    "1": ["s_1_1.txt", "s_1_2.txt"],
//            "2": ["s_2_1.txt", "s_2_2.txt"]
//		]	
//	}
//    
//    /**
//     * A real example with a non-numeric sort group
//     */
//    @Test
//    void testRealSplitWithNonNumericSort() {
//		def inputs = [
//                      "SureSelect_Capture_11MG2107_AD0AN0ACXX_GATCAG_L002_R1.fastq",
//                      "SureSelect_Capture_11MG2107_AD0AN0ACXX_GATCAG_L002_R2.fastq",
//                      "SureSelect_Capture_11MG2108_AD0AN0ACXX_TAGCTT_L002_R1.fastq",
//                      "SureSelect_Capture_11MG2108_AD0AN0ACXX_TAGCTT_L002_R2.fastq"
//                     ]
//        
//        def regex = splitter.convertPattern("CXX_%_*.fastq")
//        println "regex = $regex"
//        def result = splitter.split("CXX_%_*.fastq",inputs) 
//        
//        println "Got result:  $result"
//        
//        assert result == [
//          GATCAG:
//                ["SureSelect_Capture_11MG2107_AD0AN0ACXX_GATCAG_L002_R1.fastq", "SureSelect_Capture_11MG2107_AD0AN0ACXX_GATCAG_L002_R2.fastq"],
//          TAGCTT:
//                ["SureSelect_Capture_11MG2108_AD0AN0ACXX_TAGCTT_L002_R1.fastq", "SureSelect_Capture_11MG2108_AD0AN0ACXX_TAGCTT_L002_R2.fastq"]
//        ]
//    }
//    
//    /**
//     * A real example from bpipe user Rory
//     */
//    @Test
//    void testUnderscoreTrainingStuff() {
//		def inputs = [
//                      "120206Bha_D12-530_1_sequence.fastq",
//                      "120206Bha_D12-530_2_sequence.fastq"
//                     ]
//        
//        def pattern = "530_%_sequence.fastq"
//        def regex = splitter.convertPattern(pattern)
//        println "regex = $regex"
//        def result = splitter.split(pattern,inputs) 
//        
//        println "Got result:  $result"
//        
//        assert result == [
//          "1": ["120206Bha_D12-530_1_sequence.fastq"], "2": ["120206Bha_D12-530_2_sequence.fastq"]
//        ]
//    }
//    
//    /**
//     * A real example from Nadia / RNA seq data
//     */
//    @Test
//    void testRealSplitOnRNASeqData() {
//		def inputs = [
//                      "temp_Blasto_female_1_Ac02g4acxx_CAGATC_L003_R1.fastq_paired",
//                      "temp_Blasto_female_1_Ac02g4acxx_CAGATC_L003_R1.fastq_unpaired",
//                      "temp_Blasto_female_1_Ac02g4acxx_CAGATC_L003_R2.fastq_paired",
//                      "temp_Blasto_female_1_Ac02g4acxx_CAGATC_L003_R2.fastq_unpaired"
//                     ]
//        
//        def pattern = "Blasto_%_L*.fastq_paired"
//        def regex = splitter.convertPattern(pattern)
//        
//        println "regex = $regex"
//        def result = splitter.split(pattern,inputs) 
//        
//        println "Got result:  $result"
//        assert result == [
//          female_1_Ac02g4acxx_CAGATC : 
//              ["temp_Blasto_female_1_Ac02g4acxx_CAGATC_L003_R1.fastq_paired", 
//               "temp_Blasto_female_1_Ac02g4acxx_CAGATC_L003_R2.fastq_paired"],
//        ]
//    }
//  
//	@Test
//	public void testSort() {
//        
//        // TODO: this actually fails with 
//		// 17 as a group - why?
//		def l1 = [
//		        	"s_1_1.txt",
//					 "s_1_2.txt",
//					 "s_1_6.txt",
//					 "s_1_3.txt",
////					 "s_1_17.txt",
//					 "s_1_7.txt"
//                 ]
//			
//        def result = splitter.sortNumericThenLexically("_([^_]*)_(.*)",0, l1)
//        assert result == [
//		        	 "s_1_1.txt",
//					 "s_1_2.txt",
//					 "s_1_3.txt",
//					 "s_1_6.txt",
//					 "s_1_7.txt"
////					 "s_1_17.txt"
//                 ]
//	}
//	
//	@Test
//	public void testNoTrailing() {
//        def pattern =  splitter.convertPattern("_%_*") 
//		assert pattern == ["_(.*?)_(.*)",0]
//        def matches = ("_foo_bar" =~ pattern[0])
//        assert matches[0][1] == "foo"
//        assert matches[0][2] == "bar"
//	}
//    
//
//	@Test
//	public void testTrailing() {
//        def pattern =  splitter.convertPattern("_%_*_") 
//		assert pattern == ["_(.*?)_(.*?)_",0]
//        def matches = ("_foo_bar_" =~ pattern[0])
//        assert matches[0][1] == "foo"
//        assert matches[0][2] == "bar"
//	}//	
//
//	@Test
//	public void testNoStar() {
//        def pattern =  splitter.convertPattern("_%_") 
//		assert pattern == ["_(.*?)_",0]
//        def matches = ("_foo_bar_" =~ pattern[0])
//        assert matches[0][1] == "foo"
//	}
//    
//	@Test
//	public void testTwoStar() {
//        def pattern =  splitter.convertPattern("*_%_*") 
//		assert pattern == ["(.*?)_(.*?)_(.*)",1]
//        def matches = ("cat_foo_bar" =~ pattern[0])
//        assert matches[0][1] == "cat"
//        assert matches[0][2] == "foo"
//        assert matches[0][3] == "bar"
//	}//
//	@Test
//	public void testTwoStarTrailingUnderscore() {
//        def pattern =  splitter.convertPattern("*_%_*_") 
//		assert pattern == ["(.*?)_(.*?)_(.*?)_",1]
//        def matches = ("cat_foo_bar_" =~ pattern[0])
//        assert matches[0][1] == "cat"
//        assert matches[0][2] == "foo"
//        assert matches[0][3] == "bar"
//	}
//    
//
//	@Test
//	public void testWithExtension() {
//        def pattern =  splitter.convertPattern(/_%_*.txt/) 
//		assert pattern == ["_(.*?)_(.*?)\\.txt",0]
//	}
	
}
