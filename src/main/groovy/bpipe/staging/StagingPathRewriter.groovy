package bpipe.staging

import java.util.regex.Matcher
import java.util.regex.Pattern

import groovy.transform.CompileStatic

/**
 * Replaces whole-path occurrences of file paths in a command with staged paths.
 * <p>
 * A path is only replaced where it appears as a complete token: preceded by the start of
 * the command or a delimiter, and followed by the end of the command or a delimiter.
 * Delimiters are whitespace, quotes and shell / argument punctuation
 * (<code>= , : ; ( ) &lt; &gt; | &amp; `</code>). So <code>x.bam</code> is not replaced
 * inside <code>x.bam.bai</code> or <code>/data/x.bam</code>, and derived values such as
 * <code>$input.prefix</code> are left unchanged.
 * <p>
 * All paths are replaced in a single pass, longest first, so replaced text is never
 * itself rewritten.
 */
@CompileStatic
class StagingPathRewriter {

    private static final String DELIMITERS = "\\s'\"=,:;()<>|\\&`"

    static class Result {

        String text

        /**
         * The original paths that were found and replaced
         */
        Set<String> replaced = new LinkedHashSet<String>()
    }

    /**
     * @param text          the command text
     * @param replacements  path in command => replacement
     */
    static Result rewrite(String text, Map<String,String> replacements) {

        Result result = new Result(text: text)

        List<String> paths = replacements.keySet().findAll { it }.sort { String a, String b -> b.size() <=> a.size() }
        if(paths.isEmpty())
            return result

        String alternatives = paths.collect { Pattern.quote(it) }.join('|')
        Pattern pattern = Pattern.compile("(?<![^${DELIMITERS}])(?:${alternatives})(?![^${DELIMITERS}])")

        Matcher matcher = pattern.matcher(text)
        StringBuffer rewritten = new StringBuffer(text.size())
        while(matcher.find()) {
            String path = matcher.group()
            result.replaced.add(path)
            matcher.appendReplacement(rewritten, Matcher.quoteReplacement(replacements[path]))
        }
        matcher.appendTail(rewritten)

        result.text = rewritten.toString()
        return result
    }
}
