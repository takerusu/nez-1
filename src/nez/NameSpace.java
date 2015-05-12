package nez;

import java.io.IOException;
import java.util.HashMap;

import nez.ast.SourcePosition;
import nez.ast.Tag;
import nez.expr.Expression;
import nez.expr.Factory;
import nez.expr.GrammarChecker;
import nez.expr.NezParser;
import nez.expr.Production;
import nez.peg.dtd.DTDConverter;
import nez.util.ConsoleUtils;
import nez.util.UList;
import nez.util.UMap;

public class NameSpace {
	private static int nsid = 0;
	private static HashMap<String, NameSpace> nsMap = new HashMap<String, NameSpace>();

	public final static boolean isLoaded(String urn) {
		return nsMap.containsKey(urn);
	}

	public static NameSpace newNameSpace() {
		return newNameSpace(null);
	}

	public final static NameSpace newNameSpace(String urn) {
		if(urn != null && nsMap.containsKey(urn)) {
			return nsMap.get(urn);
		}
		NameSpace ns = new NameSpace(nsid++, urn);
		if(urn != null) {
			nsMap.put(urn, ns);
		}
		return ns;
	}
	
	// static 
	
	public final static NameSpace load(String file) throws IOException {
		return load(file, new GrammarChecker());
	}

	public final static NameSpace load(String file, GrammarChecker checker) throws IOException {
		NezParser parser = new NezParser();
		return parser.loadGrammar(SourceContext.newFileContext(file), checker);
	}
	
	public final static NameSpace loadGrammar(String file) throws IOException {
		return loadGrammar(file, new GrammarChecker());
	}
	
	public final static NameSpace loadGrammar(String file, GrammarChecker checker) throws IOException {
		if(file.endsWith(".dtd")) {
			return DTDConverter.loadGrammar(file, checker);
		}
		return load(file, checker);
	}

	
	
	final int             id;
	final String          urn;
	final String          ns;
	final UMap<Production>      ruleMap;
	final UList<String>   nameList;

	private NameSpace(int id, String urn) {
		this.id = id;
		this.urn = urn;
		String ns = "g";
		if(urn != null) {
			int loc = urn.lastIndexOf('/');
			if(loc != -1) {
				ns = urn.substring(loc+1);
			}
			ns = ns.replace(".nez", "");
		}
		this.ns = ns;
		this.ruleMap = new UMap<Production>();
		this.nameList = new UList<String>(new String[8]);
	}

	public final String uniqueName(String localName) {
		return this.ns + ":" + localName;
	}

	public final String getURN() {
		return this.urn;
	}
	
	public final boolean hasProduction(String localName) {
		return this.ruleMap.get(localName) != null;
	}

	public final void addProduction(Production p) {
		this.ruleMap.put(p.getUniqueName(), p);
	}

	public final Production defineProduction(SourcePosition s, String localName, Expression e) {
		if(!hasProduction(localName)) {
			nameList.add(localName);
		}
		Production p = new Production(s, this, localName, e);
		this.ruleMap.put(localName, p);
		addProduction(p);
		return p;
	}

	public final Production inportProduction(String ns, Production p) {
		if(ns != null) {
			String nsName = ns + "." + p.getLocalName();
			this.ruleMap.put(nsName, p);
		}
		else {
			this.ruleMap.put(p.getLocalName(), p);
		}
		addProduction(p);
		return p;
	}
	
	public final Production getProduction(String ruleName) {
		return this.ruleMap.get(ruleName);
	}
	
	
		
//	public int getRuleSize() {
//		return this.ruleMap.size();
//	}



	public final Production newRule(String name, Expression e) {
		Production r = new Production(null, this, name, e);
		this.ruleMap.put(name, r);
		return r;
	}

	public final UList<Production> getDefinedRuleList() {
		UList<Production> ruleList = new UList<Production>(new Production[this.nameList.size()]);
		for(String n : nameList) {
			ruleList.add(this.getProduction(n));
		}
		return ruleList;
	}

	public final UList<Production> getRuleList() {
		UList<Production> ruleList = new UList<Production>(new Production[this.ruleMap.size()]);
		for(String n : this.ruleMap.keys()) {
			ruleList.add(this.getProduction(n));
		}
		return ruleList;
	}

	public final Grammar2 newProduction(String name, int option) {
		Production r = this.getProduction(name);
		if(r != null) {
			return new Grammar2(r, option);
		}
		//System.out.println("** " + this.ruleMap.keys());
		return null;
	}

	public final Grammar2 newProduction(String name) {
		return this.newProduction(name, Grammar2.DefaultOption);
	}

	public void dump() {
		for(Production r : this.getRuleList()) {
			ConsoleUtils.println(r);
		}
	}
	
	
	// Grammar
	
	private SourcePosition src() {
		return null; // TODO
	}
	
	public final Expression newNonTerminal(String name) {
		return Factory.newNonTerminal(src(), this, name);
	}
	
	public final Expression newEmpty() {
		return Factory.newEmpty(src());
	}

	public final Expression newFailure() {
		return Factory.newFailure(src());
	}

	public final Expression newByteChar(int ch) {
		return Factory.newByteChar(src(), ch);
	}
	
	public final Expression newAnyChar() {
		return Factory.newAnyChar(src());
	}
	
	public final Expression newString(String text) {
		return Factory.newString(src(), text);
	}
	
	public final Expression newCharSet(SourcePosition s, String text) {
		return Factory.newCharSet(src(), text);
	}

	public final Expression newByteMap(boolean[] byteMap) {
		return Factory.newByteMap(src(), byteMap);
	}
	
	public final Expression newSequence(Expression ... seq) {
		UList<Expression> l = new UList<Expression>(new Expression[8]);
		for(Expression p: seq) {
			Factory.addSequence(l, p);
		}
		return Factory.newSequence(src(), l);
	}

	public final Expression newChoice(Expression ... seq) {
		UList<Expression> l = new UList<Expression>(new Expression[8]);
		for(Expression p: seq) {
			Factory.addChoice(l, p);
		}
		return Factory.newChoice(src(), l);
	}

	public final Expression newOption(Expression ... seq) {
		return Factory.newOption(src(), newSequence(seq));
	}
		
	public final Expression newRepetition(Expression ... seq) {
		return Factory.newRepetition(src(), newSequence(seq));
	}

	public final Expression newRepetition1(Expression ... seq) {
		return Factory.newRepetition1(src(), newSequence(seq));
	}

	public final Expression newAnd(Expression ... seq) {
		return Factory.newAnd(src(), newSequence(seq));
	}

	public final Expression newNot(Expression ... seq) {
		return Factory.newNot(src(), newSequence(seq));
	}
	
//	public final Expression newByteRange(int c, int c2) {
//		if(c == c2) {
//			return newByteChar(s, c);
//		}
//		return internImpl(s, new ByteMap(s, c, c2));
//	}
	
	// PEG4d
	public final Expression newMatch(Expression ... seq) {
		return Factory.newMatch(src(), newSequence(seq));
	}
	
	public final Expression newLink(Expression ... seq) {
		return Factory.newLink(src(), newSequence(seq), -1);
	}

	public final Expression newLink(int index, Expression ... seq) {
		return Factory.newLink(src(), newSequence(seq), index);
	}

	public final Expression newNew(Expression ... seq) {
		return Factory.newNew(src(), false, newSequence(seq));
	}

	public final Expression newLeftNew(Expression ... seq) {
		return Factory.newNew(src(), true, newSequence(seq));
	}

	public final Expression newTagging(String tag) {
		return Factory.newTagging(src(), Tag.tag(tag));
	}

	public final Expression newReplace(String msg) {
		return Factory.newReplace(src(), msg);
	}
	
	// Conditional Parsing
	// <if FLAG>
	// <with FLAG e>
	// <without FLAG e>
	
	public final Expression newIfFlag(String flagName) {
		return Factory.newIfFlag(src(), flagName);
	}
	
	public final Expression newWithFlag(String flagName, Expression ... seq) {
		return Factory.newWithFlag(src(), flagName, newSequence(seq));
	}

	public final Expression newWithoutFlag(String flagName, Expression ... seq) {
		return Factory.newWithoutFlag(src(), flagName, newSequence(seq));
	}

	public final Expression newScan(SourcePosition s, int number, Expression scan, Expression repeat) {
		return null;
	}
	
	public final Expression newRepeat(SourcePosition s, Expression e) {
		return null;
	}
	
	public final Expression newBlock(Expression ... seq) {
		return Factory.newBlock(src(), newSequence(seq));
	}

	public final Expression newDefSymbol(SourcePosition s, String table, Expression ... seq) {
		return Factory.newDefSymbol(src(), Tag.tag(table), newSequence(seq));
	}

	public final Expression newIsSymbol(SourcePosition s, String table) {
		return Factory.newIsSymbol(src(), Tag.tag(table));
	}
	
	public final Expression newIsaSymbol(SourcePosition s, String table) {
		return Factory.newIsaSymbol(src(), Tag.tag(table));
	}

	public final Expression newDefIndent(SourcePosition s) {
		return Factory.newDefIndent(src());
	}

	public final Expression newIndent(SourcePosition s) {
		return Factory.newIndent(src());
	}



}
