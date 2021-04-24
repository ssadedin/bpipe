/*
 * Copyright (c) 2013 MCRI, authors
 *
 * Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without
 * restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following
 * conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
 * OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR
 * THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package bpipe;

import java.util.List;
import java.util.regex.Pattern;

/**
 * A class for utils written in Java instead of Groovy
 * with the intention that they can be faster.
 * 
 * @author simon.sadedin@mcri.edu.au
 */
public class FastUtils {
    
    public static Pattern globToRegex(String pattern) {
        final int len = pattern.length();
        StringBuilder output = new StringBuilder(len+10);
        // Including the ^ without .* causes problems when inputs come from
        // other directories. It would be better to trim leading dirs
        // but for now allow open ended matching at the start of string
        output.append("^.*");
        for(int i=0; i<len; ++i) {
            char c = pattern.charAt(i);
            switch(c) {
              case '*':
                  output.append(".*");
                  break;
              case '?':
                  output.append(".");
                  break;
              case '.':
                  output.append("\\.");
                  break;
              default:
                  output.append(c);
            }
        }
        output.append('$');
        return Pattern.compile(output.toString());
    }
    
    /**
     * @return Return a string consisting of given values concatenated with dots,
     *         trimming any leading or trailing dots to ensure there are no 
     *         "double dots" in the resulting string. The list of values
     *         can contain nulls, which are skipped in forming the 
     *         output value.
     */
    static String dotJoin(final String... values) {
        StringBuilder result = new StringBuilder(128);
        boolean first = true;
        for(String value : values) {
            
            if(value == null)
                continue;
            
            if(first) {
                first = false;
                if(value.startsWith("."))
                    value = value.substring(1);
            }
            
            if(value.endsWith(".")) {
                value = value.substring(0, value.length()-1);
            }
            
            if(value.startsWith(".")) {
                result.append(value);
            }
            else {
              if(result.length() > 0 && !value.isEmpty()) {
                  result.append(".");
              }
              result.append(value);
            }
        }
        return result.toString(); 
    }    
    
    static String dotJoin(final List<String> values) {
        StringBuilder result = new StringBuilder(128);
        boolean first = true;
        for(String value : values) {
            
            if(value == null)
                continue;
            
            if(first) {
                first = false;
                if(value.startsWith("."))
                    value = value.substring(1);
            }
            
            if(value.endsWith(".")) {
                value = value.substring(0, value.length()-1);
            }
            
            if(value.startsWith(".")) {
                result.append(value);
            }
            else {
              if(result.length() > 0 && !value.isEmpty()) {
                  result.append(".");
              }
              result.append(value);
            }
        }
        return result.toString(); 
    }    
    
    
    public static String strip(String value, String c) {
        
        char trimChar = c.charAt(0);
        
        int trimStart = 0;
        while(trimStart<value.length() && value.charAt(trimStart) == trimChar)
            ++trimStart;
        
        int trimEnd = value.length()-1;
        while(trimEnd>=0 && value.charAt(trimEnd) == trimChar)
            --trimEnd;
        
        if(trimEnd<trimStart)
            return "";
        
        return value.substring(trimStart,trimEnd+1);
    }
    
}
