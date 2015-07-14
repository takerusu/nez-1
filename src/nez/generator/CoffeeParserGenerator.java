package nez.generator;

import jdk.nashorn.internal.runtime.regexp.RegExp;

import com.sun.org.apache.bcel.internal.generic.POP;
import com.sun.org.apache.bcel.internal.generic.PUSH;
import com.sun.org.apache.xpath.internal.functions.FunctionDef1Arg;

import nez.NezOption;
import nez.lang.And;
import nez.lang.AnyChar;
import nez.lang.Block;
import nez.lang.ByteChar;
import nez.lang.ByteMap;
import nez.lang.Capture;
import nez.lang.CharMultiByte;
import nez.lang.Choice;
import nez.lang.DefIndent;
import nez.lang.DefSymbol;
import nez.lang.ExistsSymbol;
import nez.lang.Expression;
import nez.lang.Grammar;
import nez.lang.IsIndent;
import nez.lang.IsSymbol;
import nez.lang.Link;
import nez.lang.LocalTable;
import nez.lang.New;
import nez.lang.NonTerminal;
import nez.lang.Not;
import nez.lang.Option;
import nez.lang.Production;
import nez.lang.Repetition;
import nez.lang.Repetition1;
import nez.lang.Replace;
import nez.lang.Sequence;
import nez.lang.Tagging;
import nez.util.StringUtils;

public class CoffeeParserGenerator extends ParserGenerator {

	@Override
	public String getDesc() {
		return "a Nez parser generator for JavaScript (sample)";
	}
	
	@Override
	public void generate(nez.lang.Grammar grammar, NezOption option, String fileName) {
		this.setOption(option);
		this.setOutputFile(fileName);
		makeHeader(grammar);
		Class("Parser").Open();
		generateParserClass();
		for(Production p : grammar.getProductionList()) {
			visitProduction(p);
		}
		Close();
		makeFooter(grammar);
		file.writeNewLine();
		file.flush();
	};
	
	@Override
	public void makeHeader(Grammar g) {
//		Let("input", "\'\'");

	}
	
	@Override
	public void makeFooter(Grammar g) {
		L("module.exports = new Parser()");
//		L("p = new Parser()");
//		L("o = p.parse(input)");
//		Print("JSON.stringify(o, null, \"  \")");
	}
	
	protected void generateParserClass() {
		L("constructor: ->").Open();
		Let("@currPos", "0");
		L("").Close();
		L("parse: (input) ->").Open();
		Let("@currPos", "0");
		Let("@input", "input");
		L("@nez$File()");
		L("").Close();
	}

	@Override
	public void visitEmpty(Expression p) {
		// TODO Auto-generated method stub

	}

	@Override
	public void visitFailure(Expression p) {
		Let("result", "false");

	}

	@Override
	public void visitAnyChar(AnyChar p) {
		If(MoreThan("@input.length", _pos())).Open();
		L(_inc(_pos()));
		Let(_result(), _true());
		Close();
		Else().Open();
		Let("result", "false");
		Close();

	}

	@Override
	public void visitByteChar(ByteChar p) {
		char c = (char)p.byteChar;
		If(_regex(c) + "." + callFunc("test", "@input.charAt(@currPos)"));
		Open();
		L(_inc(_pos()));
		Let(_result(), _true());
		Close();
		Else().Open();
		Let("result", "false");
		Close();
	}

	@Override
	public void visitByteMap(ByteMap p) {
		If(_regex(p.toString())).W(".").W(callFunc("test", "@input.charAt(@currPos)"));
		Open();
		Let(_result(), "true");
		L(_inc(_pos()));
		Close();
		Else().Open();
		Let("result", "false");
		Close();
	}

	@Override
	public void visitOption(Option p) {
		Let("pos" + p.getId(), _pos());
		Let("posl" + p.getId(), "poss.length");
		visitExpression(p.get(0));
		If(StEq(_result(), "false")).Open();
		Let(_pos() ,"pos" + p.getId());
		While(MoreThan("poss.length", "posl" + p.getId())).Open();
		Pop();
		Close();
		Close();
		Let(_result(), _true());
	}

	@Override
	public void visitRepetition(Repetition p) {
		While(StNotEq("result", "false"));
		Open();
		Let("posl" + p.getId(), "poss.length");
		Let("pos" + p.getId(), _pos());
		for (Expression e : p) {
			visitExpression(e);
		}
		Close();
		Let(_pos() ,"pos" + p.getId());
		While(MoreThan("poss.length", "posl" + p.getId())).Open();
		Pop();
		Close();
		Let("result", "true");
	}

	@Override
	public void visitRepetition1(Repetition1 p) {
		for (Expression e : p) {
			visitExpression(e);
		}
		If(StNotEq("result", "false")).Open();
		While(StNotEq("result", "false"));
		Open();
		Let("pos" + p.getId(), _pos());
		Let("posl" + p.getId(), "poss.length");
		for (Expression e : p) {
			visitExpression(e);
		}
		Close();
		Let(_pos() ,"pos" + p.getId());
		While(MoreThan("poss.length", "posl" + p.getId())).Open();
		Pop();
		Close();
		Let("result", "true");
		Close();


	}

	@Override
	public void visitAnd(And p) {
		Let("pos" + p.getId(), _pos());
		Let("outs" + p.getId(), _outobj());
		visitExpression(p.get(0));
		Let(_pos(), "pos" + p.getId());
		If(StEq(_result(), "false")).Open();
		Let(_result(), "false");
		Close();
		Else().Open();
		Let(_result(), "true");
		Close();
	}

	@Override
	public void visitNot(Not p) {
		Let("pos" + p.getId(), _pos());
		visitExpression(p.get(0));
		Let(_pos(), "pos" + p.getId());
		If(StEq(_result(), "false")).Open();
		Let(_result(), "true");
		Close();
		Else().Open();
		Let(_result(), "false");
		Close();
	}
	private boolean isLeftNew = false;
	private int cntNew = 0;
	@Override
	public void visitSequence(Sequence p) {
		boolean isFirst = true;
		boolean inLeftSequence = false;
		for(Expression s: p) {
			if(s instanceof New) {
				if (isLeftNew) {
					cntNew++;
				}
				if(((New)s).lefted) {
					Let(_ltmp(), "[]");
					inLeftSequence = true;
					isLeftNew = true;
				}
				visitExpression(s);
				continue;
			}
			if(s instanceof Tagging) {
				visitExpression(s);
				continue;
			}
			if(s instanceof Replace) {
				visitExpression(s);
				continue;
			}
			if(!isFirst) {
				If(StNotEq("result", "false"));
				Open();
				visitExpression(s);
				Close();
			} else {
				visitExpression(s);
				isFirst = false;
			}
			if(s instanceof Capture) {
				if (isLeftNew) {
					if(cntNew > 0) {
						cntNew--;
					} else {
						If(StNotEq(_result(), "false")).Open();
						If(_obj() + "?").Open();
						If(StNotEq("typeof " + _obj(), "\"boolean\"")).Open();
						Let(_obj() + ".value", _ltmp());
						Let(_ltmp(), "[]");
						//.W(" if ").W(StNotEq(_obj() + ".value", _outobj()));
						//Let(_result(), _obj());
						L(_outobj() + ".push " + _result() + " if " + StNotEq("typeof " + _result(), "\"boolean\""));
						Close();
						Close();
						Close();
						isLeftNew = false;
					}
				}
			}
			if (inLeftSequence) {
				If(MoreThan(_ltmp() + ".length", "0")).Open();
				L(_outobj()).W(".push ltmp.pop()");
				Close();
			}
		}
	}

	@Override
	public void visitChoice(Choice p) {
		boolean isFirst = true;
		for (Expression e : p) {
			if(!isFirst) {
				If(StEq("result", "false")).Open();
				Let(_pos(), "pos" + p.getId());
				While(MoreThan("poss.length", "posl" + p.getId())).Open();
				Pop();
				Close();
				Let(_result(), _true());
				visitExpression(e);
			} else {
				Let("pos" + p.getId(), _pos());
				Let("posl" + p.getId(), "poss.length");
				Let(_result(), _true());
				visitExpression(e);
				isFirst = false;
			}
		}
		for (int i=0; i<p.size()-1; i++) {
			Close();
		}
	}

	@Override
	public void visitNonTerminal(NonTerminal p) {
		Let("result", callFunc("@nez$" + p.getLocalName()));
		//Print("result");
	}

	@Override
	public void visitCharMultiByte(CharMultiByte p) {
		// TODO Auto-generated method stub

	}

	@Override
	public void visitLink(Link p) {
		visitExpression(p.get(0));
		if(isLeftNew) {
			L(_ltmp() + ".push " + _result() + " if " + StNotEq("typeof " + _result(), "\"boolean\""));
		} else {
			L(_outobj() + ".push " + _result() + " if " + StNotEq("typeof " + _result(), "\"boolean\""));
		}
	}

	@Override
	public void visitNew(New p) {
		if(p.lefted) {
			If(StNotEq(_ltmp() + "[" + _ltmp() + ".length-1" + "]", _result())).Open();
			L(_ltmp() + ".push " + _result() + " if " + StNotEq("typeof " + _result(), "\"boolean\""));
			Close();
		}
		Push();
		makePosObj();
	}

	@Override
	public void visitCapture(Capture p) {
		setEndPos();
		makeObject();
		Pop();
	}

	@Override
	public void visitTagging(Tagging p) {
		Let(_tag(), "\"" + p.getTagName() + "\" if "+ StNotEq(_result(), _false()));
	}

	@Override
	public void visitReplace(Replace p) {
		If(StNotEq(_result(), _false())).Open();
		Let(_result(), StringUtils.quoteString('"', p.value, '"'));
		Close();

	}

	@Override
	public void visitBlock(Block p) {
		// TODO Auto-generated method stub

	}

	@Override
	public void visitDefSymbol(DefSymbol p) {
		// TODO Auto-generated method stub

	}

	@Override
	public void visitIsSymbol(IsSymbol p) {
		// TODO Auto-generated method stub

	}

	@Override
	public void visitDefIndent(DefIndent p) {
		// TODO Auto-generated method stub

	}

	@Override
	public void visitIsIndent(IsIndent p) {
		// TODO Auto-generated method stub

	}

	@Override
	public void visitExistsSymbol(ExistsSymbol p) {
		// TODO Auto-generated method stub

	}

	@Override
	public void visitLocalTable(LocalTable p) {
		// TODO Auto-generated method stub

	}

	@Override
	public void visitProduction(Production r) {
		FuncDecl(r).Open();
		Let(_obj(), "null");
		Let(_outobj(), "[]");
		Let("poss", "[]");
		Let("tags", "[]");
		Let(_tag(), "\"\"");
		visitExpression(r.getExpression());
		If(StNotEq(_result(), "false")).Open();
		If(StEq(_obj(), "null")).Open();
		makeObject();
		Close();
		Let(_result(), _obj());
		Close();
		L("result");
		Close();
		L("");
	}
	
	protected void makeObject() {
		Let(_obj(), "{}");
		Let(_result(), _true());
		If(StEq(_tag(), "\"\"")).Open();
		If(MoreThan(_outobj() + ".length", "0")).Open();
		Let(_obj(), _outobj() + ".pop()");
		Close();
		ElseIf("@obj?").Open();
		Let(_obj(), "@obj");
		Close();
		Close();
		
		Else().Open();
		Let("obj.tag", _tag());
		Let("obj.pos", "posobj").W(" if posobj?");
		If(StNotEq(_outobj() + ".length", "0") + " and " + StEq("poss.length", "0")).Open();
		Let("obj.value", _outobj());
		Let("@obj", _obj());
		Close();
		ElseIf("posobj?").Open();
		Let("posobj.end", _pos() + " if !posobj.end?");
		Let("obj.value", _slice());
		Let("@obj", _obj());
		Close();
		Else().Open();
		Let(_obj(), "@obj if @obj?");
		Close();
		Close();
		Let("posobj", "null");
		If("!obj.value?" + " or " + StEq("obj.value", "\"\"")).Open();
		Let("obj", "true");
		Close();
		//Let(_outobj(), "[" + _obj() + "]").W(" if obj isnt true");
		Let(_result(), _obj());
	}
	
	protected CoffeeParserGenerator L(String line) {
		file.writeIndent(line);
		return this;
	}
	
	protected CoffeeParserGenerator W(String word) {
		file.write(word);
		return this;
	}
	
	protected CoffeeParserGenerator Open() {
		file.incIndent();
		return this;
	}
	
	protected CoffeeParserGenerator Close() {
		file.decIndent();
		return this;
	}
	
	protected CoffeeParserGenerator Class(String name) {
	L("class ").W(name);
		return this;
	}
	
	protected CoffeeParserGenerator If(String cond) {
		L("if(").W(cond).W(")");
		return this;
	}
	
	protected CoffeeParserGenerator ElseIf(String cond) {
		L("else if(").W(cond).W(")");
		return this;
	}
	
	protected CoffeeParserGenerator Else() {
		L("else");
		return this;
	}
	
	protected CoffeeParserGenerator While(String cond) {
		L("while(").W(cond).W(")");
		return this;
	}
	
	protected String StEq(String left, String right) {
		return left + " is " + right;
	}
	
	protected String StNotEq(String left, String right) {
		return left + " isnt " + right;
	}
	
	protected String MoreThan(String left, String right) {
		return left + " > " + right;
	}
	
	protected String MoreThanEq(String left, String right) {
		return left + " >= " + right;
	}
	
	protected CoffeeParserGenerator FuncDecl(String name, String... args) {
		L("nez$").W(name).W(": (");
		Boolean isFirst = true;
		for(String arg : args) {
			if(!isFirst) {
				W(", ");
			}
			W(arg);
			isFirst = false;
		}
		W(") ->");
		return this;
	}
	
	protected CoffeeParserGenerator FuncDecl(Production p, String... args) {
		L("nez$").W(p.getLocalName()).W(": (");
		Boolean isFirst = true;
		for(String arg : args) {
			if(!isFirst) {
				W(", ");
			}
			W(arg);
			isFirst = false;
		}
		W(") ->");
		return this;
	}
	
	protected String callFunc(String name, String... args) {
		StringBuilder sb = new StringBuilder();
		for(int i = 0; i < args.length; i++) {
			sb.append(args[i]);
			if(i != args.length - 1) {
				sb.append(", ");
			}
		}
		return name + "(" + sb.toString() + ")";
	}
	
	protected CoffeeParserGenerator Let(String left, String right) {
		L(left).W(" = ").W(right);
		return this;
	}
	
	protected String _inc(String n) {
		return n + "++";
	}
	
	protected String _dec(String n) {
		return n + "--";
	}
	
	protected String _pos() {
		return "@currPos";
	}
	
	protected String _regex(int ch) {
		String pattern = intToStr(ch);
		return "/" + pattern + "/";
 	}
	
	protected String intToStr(int ch) {
		char c = (char)ch;
		switch(c) {
		case '\n' : return("\\n"); 
		case '\t' : return("\\t"); 
		case '\r' : return("\\r"); 
		case '\'' : return("\\'"); 
		case '\\' : return("\\\\");
		case '/' : return("\\/");
		case '[' : return("\\[");
		case ']' : return("\\]");
		case '.' : return("\\.");
		case '*' : return("\\*");
		case '+' : return("\\+");
		case '?' : return("\\?");
		case '&' : return("\\&");
		case '!' : return("\\!");
		case '(' : return("\\(");
		case ')' : return("\\)");
		}
		if(Character.isISOControl(c) || c > 127) {
			return(String.format("0x%02x", (int)c));
		}
		return("" + c);
	}
	
	protected String _regex(String pattern) {
		return "/" + pattern + "/";
 	}
	
	protected String _result() {
		return "result";
	}
	
	protected String _tag() {
		return "tag";
	}
	
	protected String _outobj() {
		return "outs";
	}
	
	protected String _ltmp() {
		return "ltmp";
	}
	
	protected CoffeeParserGenerator Print(String o) {
		L("console.log ").W(o);
		return this;
	}
	
	protected String _currentchar() {
		return "@input.charAt(@currPos)";
	}
	
	protected String _obj() {
		return "obj";
	}
	
	protected String _true() {
		return "true";
	}
	
	protected String _false() {
		return "false";
	}
	
	// object
	
	protected String _slice() {
		return "@input.slice(posobj.start, posobj.end)";
	}
	
	protected void makePosObj() {
		Let("posobj", "{}");
		setStartPos();
	}
	
	protected void makeTagObj(String tag) {
		Let("tagobj", "{}");
		Let("tagobj.tag", tag);
		Let("tagobj.pos", _pos());
	}
	
	protected void setStartPos() {
		Let("posobj.start", _pos());
	}
	
	protected void setEndPos() {
		Let("posobj.end", _pos() + " if posobj?");
	}
	
	protected void Push() {
		L("poss.push(posobj) if posobj?");
	}
	
	protected void Pop() {
		Let("posobj", "poss.pop(posobj) if poss.length > 0");
	}
	
	protected void backtrackObj() {
		If("posobj?").Open();
		If("posobj.end? and " + MoreThanEq("posobj.end", _pos())).Open();
		Let("posobj.end", "null");
		Close();
		If("posobj.start? and " + MoreThanEq("posobj.start", _pos())).Open();
		Let("posobj", "null");
		Close();
		Close();
	}
	



}
