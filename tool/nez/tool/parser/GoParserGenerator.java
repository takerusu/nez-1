package nez.tool.parser;

import nez.ast.Symbol;
import nez.lang.Grammar;
import nez.lang.Nez;
import nez.util.FileBuilder;
import nez.util.StringUtils;

public class GoParserGenerator extends AbstractParserGenerator {

	@Override
	protected String getFileExtension() {
		return "go";
	}

	@Override
	public void generate(Grammar g) {
		ParserGeneratorVisitor gen = new GoParserGeneratorVisitor();
		gen.generate(g.getStartProduction(), "parse");
	}

	@Override
	protected void generateHeader(Grammar g) {
		// Statement("package nez" + FileBuilder.toFileName(this.path, null,
		// null));
		Statement("package nez");
		Statement(_Comment(FileBuilder.toFileName(this.path, null, null)));
		BeginDecl("func Parse(input string) (Tree, bool)");
		Statement("pc := NewParserContext(input)");
		Statement("ok := parse(pc)");
		Return("pc.left, ok");
		EndDecl();
	}

	@Override
	protected void generateFooter(Grammar g) {
		// TODO Auto-generated method stub

	}

	@Override
	protected void initTypeMap() {
		this.addType("parse", "bool");
		this.addType("memo", "int");
		this.addType(_byteSet_(), "[256]bool");
		this.addType(_indexMap_(), "[256]byte");
		this.addType(_byteSeq_(), "[256]byte");
		this.addType(_unchoiced_(), "bool");
		this.addType(_pos_(), "int");
		this.addType(_left_(), "Tree");
		this.addType(_log_(), "interface{}");
		this.addType(_sym_(), "int");
		this.addType(_state_(), "*ParserContext");
		this.addType(_utf8_(), "[]byte");

	}

	@Override
	protected String _Null() {
		return "nil";
	}

	@Override
	protected void DeclSymbol(String name, Symbol value) {
		VarDecl("Symbol", name, "symbol.Unique(" + StringUtils.quoteString('"', value.toString(), '"') + ")");
	}

	@Override
	protected void DeclSymbol(String name, boolean b[]) {
		StringBuilder sb = new StringBuilder();
		String type = getType(_byteSet_());
		sb.append(_BeginArray("[256]bool"));
		for (int i = 0; i < 256; i++) {
			if (b[i]) {
				sb.append(_True());
			} else {
				sb.append(_False());
			}
			if (i < 255) {
				sb.append(",");
			}
		}
		sb.append(_EndArray());
		Verbose(StringUtils.stringfyCharacterClass(b));
		VarDecl(type, name, sb.toString());
	}

	@Override
	protected void DeclSymbol(String name, byte b[]) {
		StringBuilder sb = new StringBuilder();
		sb.append(_BeginArray("[256]byte"));
		for (int i = 0; i < 256; i++) {
			sb.append(_int(b[i]));
			if (i < 255) {
				sb.append(",");
			}
		}
		sb.append(_EndArray());
		VarDecl(getType(_indexMap_()), name, sb.toString());
	}

	@Override
	protected void DeclSymbol(String name, String value) {
		VarDecl("[]byte", name, "[]byte(" + StringUtils.quoteString('"', value.toString(), '"') + ")");
	}

	@Override
	protected String _function(String type) {
		return "func";
	}

	@Override
	protected String _argument(String var, String type) {
		if (type == null) {
			return var;
		}
		return var + " " + type;
	}

	@Override
	protected String _BeginArray() {
		return _BeginArray("");
	}

	protected String _BeginArray(String type) {
		return type + "{";
	}

	@Override
	protected void Statement(String stmt) {
		file.writeIndent(stmt);
	}

	@Override
	protected void If(String cond) {
		file.writeIndent("if ");
		file.write(cond);
		Begin();
	}

	@Override
	protected void While(String cond) {
		file.writeIndent();
		file.write("for ");
		file.write(cond);
		Begin();
	}

	@Override
	protected void VarDecl(String name, String expr) {
		VarDecl(this.getType(name), name, expr);
	}

	@Override
	protected void VarDecl(String type, String name, String expr) {
		if (name == null) {
			VarAssign(name, expr);
		} else {
			Statement("var " + name + " = " + expr);
		}
	}

	@Override
	protected void VarAssign(String v, String expr) {
		Statement(v + " = " + expr);
	}

	@Override
	protected void ConstDecl(String type, String name, String val) {
		if (type == null) {
			Statement("const " + name + " = " + val);
		} else {
			Statement("const " + name + " " + type + " = " + val);
		}
	}

	@Override
	protected void BeginFunc(String type, String name, String args) {
		file.writeIndent();
		file.write(_function(type));
		file.write(" ");
		file.write(name);
		file.write("(");
		file.write(args);
		file.write(")");
		file.write(" ");
		file.write(type);
		Begin();
	}

	@Override
	protected void Break() {
		file.writeIndent("break");
	}

	class GoParserGeneratorVisitor extends ParserGeneratorVisitor {
		@Override
		public Object visitEndTree(Nez.EndTree e, Object a) {
			if (e.tag != null) {
				Statement(_Func("tagTree", _symbol(e.tag)));
			}
			Statement(_Func("endTree", _Null(), "\"\"", _int(e.shift)));
			return null;
		}
	}

}
