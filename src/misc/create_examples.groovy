//
// A hack script that converts "live" examples into Wiki format
// so that examples can be edited in place and updated on the wiki
// easily.
//
new File("../../examples").eachDir { exampleDir ->
  println " Processing $exampleDir ".center(80,'-')
  println "\n"
  
  def lines = new File(exampleDir, "pipeline.groovy").readLines()
  int headerIndex = lines.findIndexOf { it.startsWith("// ------") }
  
  int endHeader = lines.findIndexOf(headerIndex+1) { !it.startsWith("//") } - 1

  
  def exampleHumanName = (lines[headerIndex] =~ '^[/ ]*-{1,} (.*$)')[0][1].replaceAll("Example: *","")
  def exampleWikiName = exampleHumanName.replaceAll(" ","")+".wiki"
  
  println "Example name is $exampleWikiName"
  
  println "Header is from $headerIndex - $endHeader"
  
  boolean firstHeader = true
  
  // Now transform to simple wiki syntax
  output =  ["#summary Bpipe Example : $exampleHumanName",
             "#sidebar Reference" ] +             
            lines[headerIndex..endHeader].collect { line ->
                 line = line.replaceAll("^// *","")
                            .replaceAll('^-{1,} (.*$)', (firstHeader ? '== $1 ==' : '=== $1 ==='))
                 firstHeader = false    
                 return line
            } +
            ["{{{"] +
            lines[(endHeader+1)..-1] +
            ["}}}"]
              
  println output.join("\n")
 
 new File(exampleWikiName).text = output.join("\n") 
  
}

