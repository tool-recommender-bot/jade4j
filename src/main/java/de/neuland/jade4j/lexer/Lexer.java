package de.neuland.jade4j.lexer;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.neuland.jade4j.lexer.token.*;
import de.neuland.jade4j.util.CharacterParser;
import de.neuland.jade4j.util.Options;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;

import de.neuland.jade4j.exceptions.JadeLexerException;
import de.neuland.jade4j.template.TemplateLoader;

public class Lexer {
    @SuppressWarnings("unused")
    private LinkedList<String> options;
    Scanner scanner;
    private LinkedList<Token> deferredTokens;
    private int lastIndents = -1;
    private int lineno;
    private LinkedList<Token> stash;
    private LinkedList<Integer> indentStack;
    private String indentRe = null;
    private boolean pipeless = false;
    @SuppressWarnings("unused")
    private boolean attributeMode;
    private Reader reader;
    private final String filename;
    private final TemplateLoader templateLoader;
    private String indentType;
    private CharacterParser characterParser;

    public Lexer(String filename, TemplateLoader templateLoader) throws IOException {
        this.filename = ensureJadeExtension(filename);
        this.templateLoader = templateLoader;
        reader = templateLoader.getReader(this.filename);
        options = new LinkedList<String>();
        scanner = new Scanner(reader);
        deferredTokens = new LinkedList<Token>();
        stash = new LinkedList<Token>();
        indentStack = new LinkedList<Integer>();
        lastIndents = 0;
        lineno = 1;
        characterParser = new CharacterParser();
    }

    public Token next() {
        handleBlankLines();
        Token token = null;
        if ((token = deferred()) != null) {
        	return token;
        }
        
        if ((token = eos()) != null) {
           return token;
        }
        
        if ((token = pipelessText()) != null) {
           return token;
        }
        
        if ((token = yield()) != null) {
            return token;
        }
        
        if ((token = doctype()) != null) {
            return token;
        }
        
        if ((token = interpolation()) != null) {
            return token;
        }
        
        if ((token = caseToken()) != null) {
            return token;
        }

        if ((token = when()) != null) {
           return token;
        }
        
        if ((token = defaultToken()) != null) {
            return token;
        }
        
        if ((token = extendsToken()) != null) {
           return token;
        }
        
        if ((token = append()) != null) {
           return token;
        }
        
        if ((token = prepend()) != null) {
           return token;
        }
        
        if ((token = block()) != null) {
            return token;
        }
        
        if ((token = include()) != null) {
            return token;
        }
        
        if ((token = includeFiltered()) != null) {
            return token;
        }

        if ((token = mixin()) != null) {
           return token;
        }
        
        if ((token = call()) != null) {
           return token;
        }
        
        if ((token = conditional()) != null) {
            return token;
        }
        
        if ((token = each()) != null) {
            return token;
        }
        
        if ((token = whileToken()) != null) {
            return token;
        }
        
        if ((token = assignment()) != null) {
            return token;
        }
        
        if ((token = tag()) != null) {
            return token;
        }
        
        if ((token = filter()) != null) {
            return token;
        }
        
        if ((token = code()) != null) {
            return token;
        }
        
        if ((token = id()) != null) {
            return token;
        }
        
        if ((token = className()) != null) {
            return token;
        }
        
        if ((token = attrs()) != null) {
            return token;
        }
        
//        if ((token = attributesBlock()) != null) {
//            return token;
//        }

        if ((token = indent()) != null) {
            return token;
        }
        
        if ((token = text()) != null) {
            return token;
        }

        if ((token = comment()) != null) {
            return token;
        }
        
        if ((token = colon()) != null) {
            return token;
        }
        
        if ((token = dot()) != null) {
            return token;
        }
        
        if ((token = textFail()) != null) {
            return token;
        }

        if ((token = fail()) != null) {
            return token;
        }


        throw new JadeLexerException("token not recognized " + scanner.getInput().substring(0, 5), filename, getLineno(),
                    templateLoader);
    }
    

    public void handleBlankLines() {
        while (scanner.isAdditionalBlankline()) {
            consume(1);
            lineno++;
        }
    }

    public void consume(int len) {
        scanner.consume(len);
    }

    public void defer(Token tok) {
        deferredTokens.add(tok);
    }

    public Token lookahead(int n) {
        int fetch = n - stash.size();
        while (fetch > 0) {
            stash.add(next());
            fetch = fetch - 1;
        }
        n = n - 1;
        return this.stash.get(n);
    }
//    /**
//     * Return the indexOf `(` or `{` or `[` / `)` or `}` or `]` delimiters.
//     *
//     * @return {Number}
//     * @api private
//     */
//
//    bracketExpression: function(skip){
//      skip = skip || 0;
//      var start = this.input[skip];
//      if (start != '(' && start != '{' && start != '[') throw new Error('unrecognized start character');
//      var end = ({'(': ')', '{': '}', '[': ']'})[start];
//      var range = characterParser.parseMax(this.input, {start: skip + 1});
//      if (this.input[range.end] !== end) throw new Error('start character ' + start + ' does not match end character ' + this.input[range.end]);
//      return range;
//    },
    private CharacterParser.Match bracketExpression(int skip){
        char start = scanner.getInput().charAt(skip);
        if(start != '(' && start != '{' && start != '[') {
            throw new JadeLexerException("unrecognized start character", filename, getLineno(), templateLoader);
        }
        Map<Character,Character> closingBrackets =  new HashMap<Character,Character>();
        closingBrackets.put('(',')');
        closingBrackets.put('{','}');
        closingBrackets.put('[',']');
        char end = closingBrackets.get(start);
        Options options = new Options();
        options.setStart(skip+1);
        CharacterParser.Match range = characterParser.parseMax(scanner.getInput(),options);
        if(scanner.getInput().charAt(range.getEnd()) != end)
            throw new JadeLexerException("start character " + start + " does not match end character " + scanner.getInput().charAt(range.getEnd()), filename, getLineno(), templateLoader);
        return range;
    }

    public int getLineno() {
        return lineno;
    }

    public void setPipeless(boolean pipeless) {
        this.pipeless = pipeless;
    }

    public Token advance() {
        Token t = this.stashed();
        return t != null ? t : next();
    }

    // TODO: use multiscan?!
    private String scan(String regexp) {
        String result = null;
        Matcher matcher = scanner.getMatcherForPattern(regexp);
        if (matcher.find(0) && matcher.groupCount() > 0) {
            int end = matcher.end();
            consume(end);
            return matcher.group(1);
        }
        return result;
    }

    // private int indexOfDelimiters(char start, char end) {
    // String str = scanner.getInput();
    // int nstart = 0;
    // int nend = 0;
    // int pos = 0;
    // for (int i = 0, len = str.length(); i < len; ++i) {
    // if (start == str.charAt(i)) {
    // nstart++;
    // } else if (end == str.charAt(i)) {
    // nend = nend + 1;
    // if (nend == nstart) {
    // pos = i;
    // break;
    // }
    // }
    // }
    // return pos;
    // }

    private Token stashed() {
        if (stash.size() > 0) {
            return stash.poll();
        }
        return null;
    }

    private Token deferred() {
        if (deferredTokens.size() > 0) {
            return deferredTokens.poll();
        }
        return null;
    }

    private Token eos() {
        if (scanner.getInput().length() > 0) {
            return null;
        }
        if (indentStack.size() > 0) {
            indentStack.poll();
            return new Outdent("outdent", lineno);
        } else {
            return new Eos("eos", lineno);
        }
    }

    private Token comment() {
        Matcher matcher = scanner.getMatcherForPattern("^\\/\\/(-)?([^\\n]*)");
        if (matcher.find(0) && matcher.groupCount() > 1) {
            boolean buffer = !"-".equals(matcher.group(1));
            Comment comment = new Comment(matcher.group(2).trim(), lineno, buffer);
            consume(matcher.end());
            this.pipeless = true;
            return comment;
        }
        return null;
    }

    private Token code() {
        Matcher matcher = scanner.getMatcherForPattern("^(!?=|-)[ \\t]*([^\\n]+)");
        if (matcher.find(0) && matcher.groupCount() > 1) {
            Expression code = new Expression(matcher.group(2), lineno);
            String type = matcher.group(1);
            code.setEscape(type.equals("="));
            code.setBuffer(type.equals("=") || type.equals("!="));

            consume(matcher.end());
            return code;
        }
        return null;
    }

//    /**
//     * Interpolated tag.
//     */
//
//    interpolation: function() {
//      if (/^#\{/.test(this.input)) {
//        var match;
//        try {
//          match = this.bracketExpression(1);
//        } catch (ex) {
//          return;//not an interpolation expression, just an unmatched open interpolation
//        }
//
//        this.consume(match.end + 1);
//        return this.tok('interpolation', match.src);
//      }
//    }
    private Token interpolation(){
        Matcher matcher = scanner.getMatcherForPattern("^#\\{/");
        if (matcher.find(0) && matcher.groupCount() > 1) {
            try {
                CharacterParser.Match match = this.bracketExpression(1);
                this.scanner.consume(match.getEnd());
                return new Interpolation(match.getSrc(),lineno);
            } catch(Exception ex){
                return null; //not an interpolation expression, just an unmatched open interpolation
            }
        }
        return null;
    }
    // code: function() {
    // var captures;
    // if (captures = /^(!?=|-)([^\n]+)/.exec(this.input)) {
    // this.consume(captures[0].length);
    // var flags = captures[1];
    // captures[1] = captures[2];
    // var tok = this.tok('code', captures[1]);
    // tok.escape = flags[0] === '=';
    // tok.buffer = flags[0] === '=' || flags[1] === '=';
    // return tok;
    // }
    // },

    private Token tag() {
        Matcher matcher = scanner.getMatcherForPattern("^(\\w[-:\\w]*)(\\/?)");
        if (matcher.find(0) && matcher.groupCount() > 0) {
            consume(matcher.end());
            Tag tok;
            String name = matcher.group(1);
            if (':' == name.charAt(name.length() - 1)) {
                name = name.substring(0, name.length() - 1);
                tok = new Tag(name, lineno);
                this.defer(new Colon(":", lineno));
                while (' ' == scanner.getInput().charAt(0))
                    scanner.consume(1);
            } else {
                tok = new Tag(name, lineno);
            }
            if (!matcher.group(2).isEmpty()) {
                tok.setSelfClosing(true);
            }
            return tok;
        }
        return null;
    }

    private Token yield() {
        Matcher matcher = scanner.getMatcherForPattern("^yield *");
        if (matcher.find(0)) {
            matcher.group(0);
            int end = matcher.end();
            consume(end);
            return new Yield("yield", lineno);
        }
        return null;
    }

    private Token filter() {
        String val = scan("^:([\\w\\-]+)");
        if (StringUtils.isNotBlank(val)) {
            this.pipeless = true;
            return new Filter(val, lineno);
        }
        return null;
    }

    private Token each() {
        Matcher matcher = scanner.getMatcherForPattern("^(?:- *)?(?:each|for) +([a-zA-Z_$][\\w$]*)(?: *, *([a-zA-Z_$][\\w$]*))? * in *([^\\n]+)");
        if (matcher.find(0) && matcher.groupCount() > 1) {
            consume(matcher.end());
            String value = matcher.group(1);
            String key = matcher.group(2);
            String code = matcher.group(3);
            Each each = new Each(value, lineno);
            each.setCode(code);
            each.setKey(key);
            return each;
        }
        return null;
        /*
		 * if (captures = /^(?:- *)?(?:each|for) +(\w+)(?: *, *(\w+))? * in
		 * *([^\n]+)/.exec(this.input)) { this.consume(captures[0].length); var
		 * tok = this.tok('each', captures[1]); tok.key = captures[2] ||
		 * '$index'; tok.code = captures[3]; return tok; }
		 */
    }

    private Token whileToken() {
        String val = scan("^while +([^\\n]+)");
        if (StringUtils.isNotBlank(val)) {
            return new While(val, lineno);
        }
        return null;
    }

    private Token conditional() {
        Matcher matcher = scanner.getMatcherForPattern("^(if|unless|else if|else)\\b([^\\n]*)");
        if (matcher.find(0) && matcher.groupCount() > 1) {
            String type = matcher.group(1);
            String condition = matcher.group(2);
            consume(matcher.end());
            if ("else".equals(type)) {
                return new Else(null, lineno);
            } else if ("else if".equals(type)) {
                return new ElseIf(condition, lineno);
            } else {
                If ifToken = new If(condition, lineno);
                ifToken.setInverseCondition("unless".equals(type));
                return ifToken;
            }
        }
        return null;
    }

	/*
	 * private Token conditionalElse() { String val = scan("^(else)"); if
	 * (StringUtils.isNotBlank(val)) { return new Filter(val, lineno); } return
	 * null; }
	 */

    private Token doctype() {
        Matcher matcher = scanner.getMatcherForPattern("^(?:!!!|doctype) *([^\\n]+)?");
        if (matcher.find(0) && matcher.groupCount() > 0) {
            consume(matcher.end());
            return new Doctype(matcher.group(1), lineno);
        }
        return null;
    }

    private Token id() {
        String val = scan("^#([\\w-]+)");
        if (StringUtils.isNotBlank(val)) {
            return new CssId(val, lineno);
        }
        return null;
    }

    private Token className() {
        String val = scan("^\\.([\\w-]+)");
        if (StringUtils.isNotBlank(val)) {
            return new CssClass(val, lineno);
        }
        return null;
    }

    private Token text() {
        String val = scan("^(?:\\| ?| )([^\\n]+)");
        if (StringUtils.isNotEmpty(val)) {
            val = scan("^(<[^\\n]*)");
        }
        if (StringUtils.isNotEmpty(val)) {
            return new Text(val, lineno);
        }
        return null;
    }
    private Token textFail() {
        String val = scan("^([^\\.\\n][^\\n]+)");
        if (StringUtils.isNotEmpty(val)) {
            return new Text(val, lineno);
        }
        return null;
    }

    private Token fail() {
        if (Pattern.compile("^ ($|\\n)").matcher(scanner.getInput()).matches()) {
          this.consume(1);
          return this.next();
        }
        throw new JadeLexerException("unexpected text " + scanner.getInput().substring(0, 5), filename, getLineno(), templateLoader);
    }

    private Token extendsToken() {
        String val = scan("^extends? +([^\\n]+)");
        if (StringUtils.isNotBlank(val)) {
            return new ExtendsToken(val, lineno);
        }
        return null;
    }

    private Token prepend() {
        String name = scan("^prepend +([^\\n]+)");
        if (StringUtils.isNotBlank(name)) {
            Block tok = new Block(name, lineno);
            tok.setMode("prepend");
            return tok;
        }
        return null;
    }

    private Token append() {
        String name = scan("^append +([^\\n]+)");
        if (StringUtils.isNotBlank(name)) {
            Block tok = new Block(name, lineno);
            tok.setMode("append");
            return tok;
        }
        return null;
    }

    private Token block() {
        Matcher matcher = scanner.getMatcherForPattern("^block\\b *(?:(prepend|append) +)?([^\\n]+)");
        if (matcher.find(0) && matcher.groupCount() > 1) {
            String val = matcher.group(1);
            String mode = StringUtils.isNotBlank(val) ? val : "replace";
            String name = matcher.group(2);
            Block tok = new Block(name, lineno);
            tok.setMode(mode);
            consume(matcher.end());
            return tok;
        }
        return null;
    }

    private Token include() {
        String val = scan("^include +([^\\n]+)");
        if (StringUtils.isNotBlank(val)) {
            return new Include(val, lineno);
        }
        return null;
    }

    private Token includeFiltered() {
        Matcher matcher = Pattern.compile("^include:([\\w\\-]+) +([^\\n]+)").matcher(scanner.getInput());
        if(matcher.matches()){
            this.consume(matcher.end());
            String filter = matcher.group(1);
            String path = matcher.group(2);
            Include tok = new Include(path, lineno);
            tok.setFilter(filter);
            return tok;
        }
        return null;
    }

    private Token caseToken() {
        String val = scan("^case +([^\\n]+)");
        if (StringUtils.isNotBlank(val)) {
            return new CaseToken(val, lineno);
        }
        return null;
    }

    private Token when() {
        String val = scan("^when +([^:\\n]+)");
        if (StringUtils.isNotBlank(val)) {
            return new When(val, lineno);
        }
        return null;
    }

    private Token defaultToken() {
        String val = scan("^(default *)");
        if (StringUtils.isNotBlank(val)) {
            return new Default(val, lineno);
        }
        return null;
    }

    private Token assignment() {
        Matcher matcher = scanner.getMatcherForPattern("^(\\w+) += *([^;\\n]+)( *;? *)");
        if (matcher.find(0) && matcher.groupCount() > 1) {
            String name = matcher.group(1);
            String val = matcher.group(2);
            consume(matcher.end());
            Assignment assign = new Assignment(val, lineno);
            assign.setName(name);
            return assign;
        }
        return null;
    }

    private Token dot() {
        this.pipeless = true;
        Matcher matcher = scanner.getMatcherForPattern("^\\.");
        if (matcher.find(0)) {
            Dot tok = new Dot(".", lineno);
            consume(matcher.end());
            return tok;
        }
        return null;
    }

    private Token mixin() {
        Matcher matcher = scanner.getMatcherForPattern("^mixin +([-\\w]+)(?: *\\((.*)\\))? *");
        if (matcher.find(0) && matcher.groupCount() > 1) {
            Mixin tok = new Mixin(matcher.group(1), lineno);
            tok.setArguments(matcher.group(2));
            consume(matcher.end());
            return tok;
        }
        return null;
    }

    private Token call() {
        Matcher matcher = scanner.getMatcherForPattern("^\\+([-\\w]+)");
        if (matcher.find(0) && matcher.groupCount() > 0) {
            Call tok = new Call(matcher.group(1), lineno);
            consume(matcher.end());

            matcher = scanner.getMatcherForPattern("^ *\\((.*?)\\)");

            if (matcher.find(0) && matcher.groupCount() > 0) {
                // verify group does not contain attributes
                Matcher attributeMatcher = Pattern.compile("^ *[-\\w]+ *=").matcher(matcher.group(1));
                if (!attributeMatcher.find(0)) {
                    tok.setArguments(matcher.group(1));
                    consume(matcher.end());
                }
            }
            return tok;
        }
        return null;
    }

    private Token attrs() {
        if ('(' != scanner.charAt(0)) {
            return null;
        }

        int index = indexOfDelimiters('(', ')');
        if (index == 0) {
            throw new JadeLexerException("invalid attribute definition; missing )", filename, getLineno(), templateLoader);
        }
        String string = scanner.getInput().substring(1, index);
        consume(index + 1);

        Attribute attribute = new AttributeLexer().getToken(string, lineno);

        if (scanner.getInput().charAt(0) == '/') {
            consume(1);
            attribute.setSelfClosing(true);
        }

        return attribute;
    }

    private int indexOfDelimiters(char start, char end) {
        String str = scanner.getInput();
        int nstart = 0;
        int nend = 0;
        int pos = 0;
        for (int i = 0, len = str.length(); i < len; i++) {
            if (start == str.charAt(i)) {
                nstart++;
            } else if (end == str.charAt(i)) {
                if (++nend == nstart) {
                    pos = i;
                    break;
                }
            }
        }
        return pos;
    }

	/*
	 * private Token attributes() { Attribute tok = new Attribute(null, lineno);
	 * Matcher matcher = scanner.getMatcherForPattern("^\\("); if
	 * (matcher.find(0)) { consume(matcher.end()); attributeMode = true; } else
	 * { return null; }
	 * 
	 * StringBuilder sb = new StringBuilder(); String regexp =
	 * "^[, ]*?([-_\\w]+)? *?= *?(\"[^\"]*?\"|'[^']*?'|[.-_\\w]+)"; matcher =
	 * scanner.getMatcherForPattern(regexp); if (matcher.find(0)) { while
	 * (matcher.find(0)) { String name = matcher.group(1); String value =
	 * matcher.group(2); tok.addAttribute(name, value);
	 * sb.append(matcher.group(0)); consume(matcher.end()); matcher =
	 * scanner.getMatcherForPattern(regexp); } tok.setValue(sb.toString()); }
	 * else { return null; }
	 * 
	 * matcher = scanner.getMatcherForPattern("^ *?\\)"); if (matcher.find(0)) {
	 * consume(matcher.end()); attributeMode = false; } else { throw new
	 * JadeLexerException
	 * ("Error while parsing attribute. Missing closing bracket ", filename,
	 * getLineno(), scanner.getInput()); } return tok; }
	 */

    private Token indent() {
        Matcher matcher;
        String re;

        if (indentRe != null) {
            matcher = scanner.getMatcherForPattern(indentRe);
        } else {
            // tabs
            re = "^\\n(\\t*) *";
            String indentType = "tabs";
            matcher = scanner.getMatcherForPattern(re);

            // spaces
            if (matcher.find(0) && matcher.group(1).length() == 0) {
                re = "^\\n( *)";
                indentType = "spaces";
                matcher = scanner.getMatcherForPattern(re);
            }

            // established
            if (matcher.find(0) && matcher.group(1).length() > 0)
                this.indentRe = re;
            this.indentType = indentType;
        }

        if (matcher.find(0) && matcher.groupCount() > 0) {
            Token tok;
            int indents = matcher.group(1).length();
            if (lastIndents <= 0 && indents > 0)
                lastIndents = indents;
            lineno++;
            consume(indents + 1);

            if ((indents > 0 && lastIndents > 0 && indents % lastIndents != 0) || scanner.isIntendantionViolated()) {
                throw new JadeLexerException("invalid indentation; expecting " + indents + " " + indentType, filename, getLineno(), templateLoader);
            }

            // blank line
            if (scanner.isBlankLine()) {
                this.pipeless = false;
                return new Newline("newline", lineno);
            }

            // outdent
            if (indentStack.size() > 0 && indents < indentStack.get(0)) {
                while (indentStack.size() > 0 && indentStack.get(0) > indents) {
                    stash.add(new Outdent("outdent", lineno));
                    indentStack.poll();
                }
                tok = this.stash.pollLast();
                // indent
            } else if (indents > 0 && (indentStack.size() < 1 || indents != indentStack.get(0))) {
                indentStack.push(indents);
                tok = new Indent("indent", lineno);
                tok.setIndents(indents);
                // newline
            } else {
                tok = new Newline("newline", lineno);
            }
            this.pipeless = false;
            return tok;
        }
        return null;
    }
    private Token pipelessText(){
        if (!this.pipeless) return null;
        Matcher matcher;
        String re;

        // established regexp
        if (this.indentRe != null) {
            matcher = scanner.getMatcherForPattern(indentRe);
            // determine regexp
        } else {
            // tabs
            re = "^\\n(\\t*) *";
            matcher = scanner.getMatcherForPattern(re);

            // spaces
            if (matcher.find(0) && matcher.group(1).length() == 0) {
                re = "^\\n( *)";
                matcher = scanner.getMatcherForPattern(re);
            }
            // established
            if (matcher.find(0) && matcher.group(1).length() > 0)
                this.indentRe = re;
        }
        if (matcher.find(0) && matcher.group(1).length() > 0) {
            int indents = matcher.group(1).length();
            if (indents > 0 && (this.indentStack.size() == 0 || indents > this.indentStack.get(0))) {
                String indent = matcher.group(1);
                ArrayList<String> tokens = new ArrayList<String>();
                boolean isMatch = false;

                do {
                    // text has `\n` as a prefix
                    int i = scanner.getInput().substring(1).indexOf('\n');
                    if (-1 == i)
                        i = scanner.getInput().length() - 1;
                    String str;
                    if(i < 1) {
                        str = scanner.getInput().substring(1);
                    }else{
                        str = scanner.getInput().substring(1,i+1);
                    }
                    int indentLength = indent.length();
                    if(str.length()<=indentLength)
                        indentLength = str.length();
                    isMatch = str.substring(0, indentLength).equals(indent) || !(str.trim().length() > 0);
                    if (isMatch) {
                        // consume test along with `\n` prefix if match
                        this.consume(str.length() + 1);
                        lineno++;
                        tokens.add(str.substring(indentLength));
                    }
                } while (scanner.getInput().length() > 0 && isMatch);
                while (scanner.getInput().length() == 0 && tokens.get(tokens.size() - 1).equals(""))
                    tokens.remove(tokens.size() - 1);
                PipelessText pipelessText = new PipelessText("pipeless-text", lineno);
                pipelessText.setValues(tokens);
                return pipelessText;
            }
        }
        return null;
    }

    private Token pipelessText2() {
        if (this.pipeless) {
            if ('\n' == scanner.getInput().charAt(0))
                return null;
            int i = scanner.getInput().indexOf('\n');
            if (-1 == i)
                i = scanner.getInput().length();
            String str = scanner.getInput().substring(0, i);
            consume(str.length());
            return new PipelessText(str, lineno);
        }
        return null;
    }

    private Token colon() {
        String val = scan("^: *");
        if (StringUtils.isNotBlank(val)) {
            return new Colon(val, lineno);
        }
        return null;
    }

    private String ensureJadeExtension(String templateName) {
        if ( StringUtils.isBlank(FilenameUtils.getExtension(templateName))) {
            return templateName + ".jade";
        }
        return templateName;
    }

    public boolean getPipeless() {
        return pipeless;
    }
}
