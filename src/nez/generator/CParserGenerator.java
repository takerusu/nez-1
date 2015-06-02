package nez.generator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Stack;

import nez.ast.Tag;
import nez.lang.And;
import nez.lang.AnyChar;
import nez.lang.ByteChar;
import nez.lang.ByteMap;
import nez.lang.Capture;
import nez.lang.Choice;
import nez.lang.Empty;
import nez.lang.Expression;
import nez.lang.Failure;
import nez.lang.Grammar;
import nez.lang.IfFlag;
import nez.lang.Link;
import nez.lang.New;
import nez.lang.NonTerminal;
import nez.lang.Not;
import nez.lang.OnFlag;
import nez.lang.Option;
import nez.lang.Production;
import nez.lang.Repetition;
import nez.lang.Repetition1;
import nez.lang.Replace;
import nez.lang.Sequence;
import nez.lang.Tagging;
import nez.vm.GrammarOptimizer;

public class CParserGenerator extends ParserGenerator {

	public CParserGenerator() {
		super(null);
	}

	public CParserGenerator(String fileName) {
		super(fileName);
	}

	@Override
	public String getDesc() {
		return "a Nez parser generator for C (sample)";
	}

	boolean PatternMatch = true;
	int option = Grammar.ASTConstruction | Grammar.Prediction;

	@Override
	public void generate(Grammar grammar) {
		makeHeader(grammar);
		for(Production p : grammar.getProductionList()) {
			visitProduction(p);
		}
		makeFooter(grammar);
		file.writeNewLine();
		file.flush();
	}

	@Override
	public void makeHeader(Grammar grammar) {
		this.file.write("// This file is generated by nez/src/nez/x/parsergenerator/CParserGenerator.java");
		this.file.writeNewLine();
		this.file.writeIndent("#include \"libnez/libnez.h\"");
		this.file.writeIndent("#include <stdio.h>");
		for(Production r : grammar.getProductionList()) {
			if(!r.getLocalName().startsWith("\"")) {
				this.file.writeIndent("int p" + r.getLocalName() + "(ParsingContext ctx);");
			}
		}
		this.file.writeNewLine();
	}

	@Override
	public void makeFooter(Grammar grammar) {
		this.file.writeIndent("int main(int argc, char* const argv[])");
		this.openBlock();
		this.file.writeIndent("uint64_t start, end;");
		this.file.writeIndent("ParsingContext ctx = nez_CreateParsingContext(argv[1]);");
		this.file.writeIndent("ctx->flags_size = " + flagTable.size() + ";");
		this.file.writeIndent("ctx->flags = (int*)calloc(" + flagTable.size() + ", sizeof(int));");
		this.file.writeIndent("createMemoTable(ctx, " + memoId + ");");
		this.file.writeIndent("start = timer();");
		this.file.writeIndent("if(pFile(ctx))");
		this.openBlock();
		this.file.writeIndent("nez_PrintErrorInfo(\"parse error\");");
		this.closeBlock();
		this.file.writeIndent("else if((ctx->cur - ctx->inputs) != ctx->input_size)");
		this.openBlock();
		this.file.writeIndent("nez_PrintErrorInfo(\"unconsume\");");
		this.closeBlock();
		this.file.writeIndent("else");
		this.openBlock();
		this.file.writeIndent("end = timer();");
		this.file.writeIndent("ParsingObject po = nez_commitLog(ctx,0);");
		this.file.writeIndent("dump_pego(&po, ctx->inputs, 0);");
		this.file.writeIndent("fprintf(stderr, \"ErapsedTime: %llu msec\\n\", (unsigned long long)end - start);");
		this.file.writeIndent("fprintf(stderr, \"match\");");
		this.closeBlock();
		this.file.writeIndent("return 0;");
		this.file.writeIndent();
		this.closeBlock();
	}

	int fid = 0;

	class FailurePoint {
		int id;
		FailurePoint prev;

		public FailurePoint(int label, FailurePoint prev) {
			this.id = label;
			this.prev = prev;
		}
	}

	FailurePoint fLabel;

	private void initFalureJumpPoint() {
		this.fid = 0;
		fLabel = null;
	}

	private void pushFailureJumpPoint() {
		this.fLabel = new FailurePoint(this.fid++, this.fLabel);
	}

	private void popFailureJumpPoint(Production r) {
		this.file.decIndent();
		this.file.writeIndent("CATCH_FAILURE" + this.fLabel.id + ":" + "/* " + " */");
		this.file.incIndent();
		this.fLabel = this.fLabel.prev;
	}

	private void popFailureJumpPoint(Expression e) {
		this.file.decIndent();
		this.file.writeIndent("CATCH_FAILURE" + this.fLabel.id + ":" + "/* " + " */");
		this.file.incIndent();
		this.fLabel = this.fLabel.prev;
	}

	private void jumpFailureJump() {
		this.file.writeIndent("goto CATCH_FAILURE" + this.fLabel.id + ";");
	}

	private void jumpPrevFailureJump() {
		this.file.writeIndent("goto CATCH_FAILURE" + this.fLabel.prev.id + ";");
	}

	private void openBlock() {
		this.file.write(" {");
		this.file.incIndent();
	}

	private void closeBlock() {
		this.file.decIndent();
		this.file.writeIndent("}");
	}

	private void gotoLabel(String label) {
		this.file.writeIndent("goto " + label + ";");
	}

	private void exitLabel(String label) {
		this.file.decIndent();
		this.file.writeIndent(label + ": ;; /* <- this is required for avoiding empty statement */");
		this.file.incIndent();
	}

	private void let(String type, String var, String expr) {
		if(type != null) {
			this.file.writeIndent(type + " " + var + " = " + expr + ";");
		}
		else {
			this.file.writeIndent("" + var + " = " + expr + ";");
		}
	}

	private void memoize(Production rule, int id, String pos) {
		this.file.writeIndent("nez_setMemo(ctx, " + pos + ", " + id + ", 0);");
	}

	private void memoizeFail(Production rule, int id, String pos) {
		this.file.writeIndent("nez_setMemo(ctx, " + pos + ", " + id + ", 1);");
	}

	private void lookup(Production rule, int id) {
		this.file.writeIndent("MemoEntry memo = nez_getMemo(ctx, ctx->cur, " + id + ");");
		this.file.writeIndent("if(memo != NULL)");
		this.openBlock();
		this.file.writeIndent("if(memo->r)");
		this.openBlock();
		this.file.writeIndent("return 1;");
		this.closeBlock();
		this.file.writeIndent("else");
		this.openBlock();
		if(!PatternMatch) {
			this.file.writeIndent("nez_pushDataLog(ctx, LazyLink_T, 0, -1, NULL, memo->left);");
		}
		this.file.writeIndent("ctx->cur = memo->consumed;");
		this.file.writeIndent("return 0;");
		this.closeBlock();
		this.closeBlock();
	}

	private void consume() {
		this.file.writeIndent("ctx->cur++;");
	}

	int memoId = 0;

	@Override
	public void visitProduction(Production rule) {
		this.initFalureJumpPoint();
		this.file.writeIndent("int p" + rule.getLocalName() + "(ParsingContext ctx)");
		this.openBlock();
		this.pushFailureJumpPoint();
		lookup(rule, memoId);
		String pos = "c" + this.fid;
		this.let("char*", pos, "ctx->cur");
		Expression e = new GrammarOptimizer(this.option).optimize(rule);
		visit(e);
		memoize(rule, memoId, pos);
		this.file.writeIndent("return 0;");
		this.popFailureJumpPoint(rule);
		memoizeFail(rule, memoId, pos);
		this.file.writeIndent("return 1;");
		this.closeBlock();
		this.file.writeNewLine();
		memoId++;
	}

	@Override
	public void visitEmpty(Empty e) {
	}

	@Override
	public void visitFailure(Failure e) {
		this.jumpFailureJump();
	}

	@Override
	public void visitNonTerminal(NonTerminal e) {
		this.file.writeIndent("if(p" + e.getLocalName() + "(ctx))");
		this.openBlock();
		this.jumpFailureJump();
		this.closeBlock();
	}

	public String stringfyByte(int byteChar) {
		char c = (char) byteChar;
		switch (c) {
		case '\n':
			return ("'\\n'");
		case '\t':
			return ("'\\t'");
		case '\r':
			return ("'\\r'");
		case '\'':
			return ("\'\\\'\'");
		case '\\':
			return ("'\\\\'");
		}
		return "\'" + c + "\'";
	}

	@Override
	public void visitByteChar(ByteChar e) {
		this.file.writeIndent("if((int)*ctx->cur != " + e.byteChar + ")");
		this.openBlock();
		this.jumpFailureJump();
		this.closeBlock();
		this.consume();
	}

	private int searchEndChar(boolean[] b, int s) {
		for(; s < 256; s++) {
			if(!b[s]) {
				return s - 1;
			}
		}
		return 255;
	}

	@Override
	public void visitByteMap(ByteMap e) {
		int fid = this.fid++;
		String label = "EXIT_BYTEMAP" + fid;
		boolean b[] = e.byteMap;
		for(int start = 0; start < 256; start++) {
			if(b[start]) {
				int end = searchEndChar(b, start + 1);
				if(start == end) {
					this.file.writeIndent("if((int)*ctx->cur == " + start + ")");
					this.openBlock();
					this.consume();
					this.gotoLabel(label);
					this.closeBlock();
				}
				else {
					this.file.writeIndent("if(" + start + "<= (int)*ctx->cur" + " && (int)*ctx->cur <= " + end + ")");
					this.openBlock();
					this.consume();
					this.gotoLabel(label);
					this.closeBlock();
					start = end;
				}
			}
		}
		this.jumpFailureJump();
		this.exitLabel(label);
	}

	@Override
	public void visitAnyChar(AnyChar e) {
		this.file.writeIndent("if(*ctx->cur == 0)");
		this.openBlock();
		this.jumpFailureJump();
		this.closeBlock();
		this.consume();
	}

	@Override
	public void visitOption(Option e) {
		this.pushFailureJumpPoint();
		String label = "EXIT_OPTION" + this.fid;
		String backtrack = "c" + this.fid;
		this.let("char*", backtrack, "ctx->cur");
		visit(e.get(0));
		this.gotoLabel(label);
		this.popFailureJumpPoint(e);
		this.let(null, "ctx->cur", backtrack);
		this.exitLabel(label);
	}

	@Override
	public void visitRepetition(Repetition e) {
		this.pushFailureJumpPoint();
		String backtrack = "c" + this.fid;
		this.let("char*", backtrack, "ctx->cur");
		this.file.writeIndent("while(1)");
		this.openBlock();
		visit(e.get(0));
		this.let(null, backtrack, "ctx->cur");
		this.closeBlock();
		this.popFailureJumpPoint(e);
		this.let(null, "ctx->cur", backtrack);
	}

	@Override
	public void visitRepetition1(Repetition1 e) {
		visit(e.get(0));
		this.pushFailureJumpPoint();
		String backtrack = "c" + this.fid;
		this.let("char*", backtrack, "ctx->cur");
		this.file.writeIndent("while(1)");
		this.openBlock();
		visit(e.get(0));
		this.let(null, backtrack, "ctx->cur");
		this.closeBlock();
		this.popFailureJumpPoint(e);
		this.let(null, "ctx->cur", backtrack);
	}

	@Override
	public void visitAnd(And e) {
		this.pushFailureJumpPoint();
		String label = "EXIT_AND" + this.fid;
		String backtrack = "c" + this.fid;
		this.let("char*", backtrack, "ctx->cur");
		visit(e.get(0));
		this.let(null, "ctx->cur", backtrack);
		this.gotoLabel(label);
		this.popFailureJumpPoint(e);
		this.let(null, "ctx->cur", backtrack);
		this.jumpFailureJump();
		this.exitLabel(label);
	}

	@Override
	public void visitNot(Not e) {
		this.pushFailureJumpPoint();
		String backtrack = "c" + this.fid;
		this.let("char*", backtrack, "ctx->cur");
		visit(e.get(0));
		this.let(null, "ctx->cur", backtrack);
		this.jumpPrevFailureJump();
		this.popFailureJumpPoint(e);
		this.let(null, "ctx->cur", backtrack);
	}

	@Override
	public void visitSequence(Sequence e) {
		for(int i = 0; i < e.size(); i++) {
			visit(e.get(i));
		}
	}

	boolean isPrediction = true;

	@Override
	public void visitChoice(Choice e) {
		if(e.predictedCase != null && isPrediction) {
			isPrediction = false;
			System.out.println("Prediction");
			int fid = this.fid++;
			String label = "EXIT_CHOICE" + fid;
			HashMap<Integer, Expression> m = new HashMap<Integer, Expression>();
			ArrayList<Expression> l = new ArrayList<Expression>();
			this.file.writeIndent("void* jump_table" + fid + "[] = {");
			for(int ch = 0; ch < e.predictedCase.length; ch++) {
				Expression pCase = e.predictedCase[ch];
				if(pCase != null) {
					Expression me = m.get(pCase.getId());
					if(me == null) {
						m.put(pCase.getId(), pCase);
						l.add(pCase);
					}
					this.file.write("&&PREDICATE_JUMP" + fid + pCase.getId());
				}
				else {
					this.file.write("&&PREDICATE_JUMP" + fid + 0);
				}
				if(ch < e.predictedCase.length - 1) {
					this.file.write(", ");
				}
			}
			this.file.write("};");
			this.file.writeIndent("goto *jump_table" + fid + "[(unsigned int)*ctx->cur];");
			for(int i = 0; i < l.size(); i++) {
				Expression pe = l.get(i);
				this.exitLabel("PREDICATE_JUMP" + fid + pe.getId());
				visit(pe);
				this.gotoLabel(label);
			}
			this.exitLabel("PREDICATE_JUMP" + fid + 0);
			this.jumpFailureJump();
			this.exitLabel(label);
			isPrediction = true;
		}
		else {
			this.fid++;
			String label = "EXIT_CHOICE" + this.fid;
			String backtrack = "c" + this.fid;
			this.let("char*", backtrack, "ctx->cur");
			for(int i = 0; i < e.size(); i++) {
				this.pushFailureJumpPoint();
				visit(e.get(i));
				this.gotoLabel(label);
				this.popFailureJumpPoint(e.get(i));
				this.let(null, "ctx->cur", backtrack);
			}
			this.jumpFailureJump();
			this.exitLabel(label);
		}
	}

	Stack<String> markStack = new Stack<String>();

	@Override
	public void visitNew(New e) {
		if(!PatternMatch) {
			this.pushFailureJumpPoint();
			String mark = "mark" + this.fid;
			this.markStack.push(mark);
			this.file.writeIndent("int " + mark + " = nez_markLogStack(ctx);");
			this.file.writeIndent("nez_pushDataLog(ctx, LazyNew_T, ctx->cur - ctx->inputs, -1, NULL, NULL);");
		}
	}

	@Override
	public void visitCapture(Capture e) {
		if(!PatternMatch) {
			String label = "EXIT_CAPTURE" + this.fid++;
			this.file.writeIndent("nez_pushDataLog(ctx, LazyCapture_T, ctx->cur - ctx->inputs, 0, NULL, NULL);");
			this.gotoLabel(label);
			this.popFailureJumpPoint(e);
			this.file.writeIndent("nez_abortLog(ctx, " + this.markStack.pop() + ");");
			this.jumpFailureJump();
			this.exitLabel(label);
		}
	}

	@Override
	protected String _tag(Tag tag) {
		return null;
	}

	@Override
	public void visitTagging(Tagging e) {
		if(!PatternMatch) {
			this.file.writeIndent("nez_pushDataLog(ctx, LazyTag_T, 0, 0, \"" + e.tag.getName() + "\", NULL);");
		}
	}

	@Override
	public void visitReplace(Replace e) {
		if(!PatternMatch) {
			this.file.writeIndent("nez_pushDataLog(ctx, LazyValue_T, 0, 0, \"" + e.value + "\", NULL);");
		}
	}

	@Override
	public void visitLink(Link e) {
		if(!PatternMatch) {
			this.pushFailureJumpPoint();
			String mark = "mark" + this.fid;
			String label = "EXIT_LINK" + this.fid;
			String po = "ctx->left"; //+ this.fid;
			this.file.writeIndent("int " + mark + " = nez_markLogStack(ctx);");
			visit(e.get(0));
			this.let(null, po, "nez_commitLog(ctx, " + mark + ")");
			this.file.writeIndent("nez_pushDataLog(ctx, LazyLink_T, 0, " + e.index + ", NULL, " + po + ");");
			this.gotoLabel(label);
			this.popFailureJumpPoint(e);
			this.file.writeIndent("nez_abortLog(ctx, " + mark + ");");
			this.jumpFailureJump();
			this.exitLabel(label);
		}
		else {
			visit(e.get(0));
		}
	}

	ArrayList<String> flagTable = new ArrayList<String>();

	public void visitIfFlag(IfFlag e) {
		if(!flagTable.contains(e.getFlagName())) {
			flagTable.add(e.getFlagName());
		}
		if(e.isPredicate()) {
			this.file.writeIndent("if(!ctx->flags[" + flagTable.indexOf(e.getFlagName()) + "])");
			this.openBlock();
			this.jumpFailureJump();
			this.closeBlock();
		}
		else {
			this.file.writeIndent("if(ctx->flags[" + flagTable.indexOf(e.getFlagName()) + "])");
			this.openBlock();
			this.jumpFailureJump();
			this.closeBlock();
		}
	}

	public void visitOnFlag(OnFlag e) {
		if(!flagTable.contains(e.getFlagName())) {
			flagTable.add(e.getFlagName());
		}
		visit(e.get(0));
		if(e.isPredicate()) {
			this.file.writeIndent("ctx->flags[" + flagTable.indexOf(e.getFlagName()) + "] = 1;");
		}
		else {
			this.file.writeIndent("ctx->flags[" + flagTable.indexOf(e.getFlagName()) + "] = 0;");
		}
	}

}
