package nez.parser.moz;

import java.util.Arrays;

import nez.ast.ASTMachine;
import nez.ast.Symbol;
import nez.lang.Expression;
import nez.lang.Production;
import nez.lang.expr.Cbyte;
import nez.lang.expr.Cmulti;
import nez.lang.expr.Cset;
import nez.lang.expr.Pchoice;
import nez.lang.expr.Tcapture;
import nez.lang.expr.Tlfold;
import nez.lang.expr.Tlink;
import nez.lang.expr.Tnew;
import nez.lang.expr.Treplace;
import nez.lang.expr.Ttag;
import nez.lang.expr.Xblock;
import nez.lang.expr.Xexists;
import nez.lang.expr.Xis;
import nez.lang.expr.Xlocal;
import nez.lang.expr.Xmatch;
import nez.lang.expr.Xsymbol;
import nez.parser.ByteCoder;
import nez.parser.MemoEntry;
import nez.parser.MemoPoint;
import nez.parser.ParseFunc;
import nez.parser.StackData;
import nez.parser.SymbolTable;
import nez.parser.TerminationException;
import nez.util.StringUtils;

public class Moz {
	public static class Fail extends MozInst {
		public Fail(Expression e) {
			super(MozSet.Fail, e, null);
		}

		@Override
		protected void encodeImpl(ByteCoder c) {
			// No argument
		}

		@Override
		public MozInst exec(MozMachine sc) throws TerminationException {
			return sc.fail();
		}
	}

	public static class Alt extends MozInst {
		public final MozInst failjump;

		public Alt(Expression e, MozInst failjump, MozInst next) {
			super(MozSet.Alt, e, next);
			this.failjump = labeling(failjump);
		}

		@Override
		MozInst branch() {
			return this.failjump;
		}

		@Override
		protected String getOperand() {
			return label(this.failjump) + "  ## " + e;
		}

		@Override
		protected void encodeImpl(ByteCoder c) {
			c.encodeJump(this.failjump);
		}

		@Override
		public MozInst exec(MozMachine sc) throws TerminationException {
			sc.pushAlt(this.failjump);
			return this.next;
		}
	}

	public static class Succ extends MozInst {
		public Succ(Expression e, MozInst next) {
			super(MozSet.Succ, e, next);
		}

		@Override
		protected void encodeImpl(ByteCoder c) {
			// No argument
		}

		@Override
		public MozInst exec(MozMachine sc) throws TerminationException {
			sc.popAlt();
			return this.next;
		}
	}

	public static class Skip extends MozInst {
		public Skip(Expression e) {
			super(MozSet.Skip, e, null);
		}

		@Override
		protected void encodeImpl(ByteCoder c) {
			// No argument
		}

		@Override
		public MozInst exec(MozMachine sc) throws TerminationException {
			return sc.skip(this.next);
		}
	}

	public static class Label extends MozInst {
		Production rule;

		public Label(Production rule, MozInst next) {
			super(MozSet.Label, rule.getExpression(), next);
			this.rule = rule;
		}

		@Override
		protected String getOperand() {
			return rule.getLocalName();
		}

		@Override
		protected void encodeImpl(ByteCoder c) {
			c.encodeNonTerminal(rule.getLocalName());
		}

		@Override
		public MozInst exec(MozMachine sc) throws TerminationException {
			return this.next;
		}
	}

	public static class Call extends MozInst {
		ParseFunc f;
		String name;
		public MozInst jump = null;

		public Call(ParseFunc f, String name, MozInst next) {
			super(MozSet.Call, null, next);
			this.f = f;
			this.name = name;
		}

		public Call(ParseFunc f, String name, MozInst jump, MozInst next) {
			super(MozSet.Call, null, jump);
			this.name = name;
			this.f = f;
			this.jump = next;
		}

		void sync() {
			if (this.jump == null) {
				this.jump = labeling(this.next);
				this.next = labeling((MozInst) f.getCompiled());
			}
			this.f = null;
		}

		public final String getNonTerminalName() {
			return this.name;
		}

		@Override
		protected String getOperand() {
			return label(jump);
		}

		@Override
		protected void encodeImpl(ByteCoder c) {
			c.encodeJump(this.jump);
			c.encodeNonTerminal(name); // debug information
		}

		@Override
		public MozInst exec(MozMachine sc) throws TerminationException {
			StackData s = sc.newUnusedStack();
			s.ref = this.jump;
			return this.next;
		}
	}

	public static class Ret extends MozInst {
		public Ret(Production e) {
			super(MozSet.Ret, e.getExpression(), null);
		}

		public Ret(Expression e) {
			super(MozSet.Ret, e, null);
		}

		@Override
		protected void encodeImpl(ByteCoder c) {
			// No argument
		}

		@Override
		public MozInst exec(MozMachine sc) throws TerminationException {
			StackData s = sc.popStack();
			return (MozInst) s.ref;
		}
	}

	public static class Pos extends MozInst {
		public Pos(Expression e, MozInst next) {
			super(MozSet.Pos, e, next);
		}

		@Override
		protected void encodeImpl(ByteCoder c) {
			// No argument
		}

		@Override
		public MozInst exec(MozMachine sc) throws TerminationException {
			StackData s = sc.newUnusedStack();
			s.value = sc.getPosition();
			return this.next;
		}
	}

	public static class Back extends MozInst {
		public Back(Expression e, MozInst next) {
			super(MozSet.Back, e, next);
		}

		@Override
		protected void encodeImpl(ByteCoder c) {
			// No argument
		}

		@Override
		public MozInst exec(MozMachine sc) throws TerminationException {
			StackData s = sc.popStack();
			sc.setPosition(s.value);
			return this.next;
		}
	}

	public static class Exit extends MozInst {
		boolean status;

		public Exit(boolean status) {
			super(MozSet.Exit, null, null);
			this.status = status;
		}

		@Override
		protected void encodeImpl(ByteCoder c) {
			c.write_b(status);
		}

		@Override
		public MozInst exec(MozMachine sc) throws TerminationException {
			throw new TerminationException(status);
		}
	}

	static abstract class AbstractByteInstruction extends MozInst {
		public final int byteChar;

		AbstractByteInstruction(byte bytecode, Cbyte e, MozInst next) {
			super(bytecode, e, next);
			this.byteChar = e.byteChar;
		}

		@Override
		protected String getOperand() {
			return StringUtils.stringfyCharacter(byteChar);
		}

		@Override
		protected void encodeImpl(ByteCoder c) {
			c.encodeByte(byteChar);
		}
	}

	public static class Byte extends AbstractByteInstruction {
		public Byte(Cbyte e, MozInst next) {
			super(MozSet.Byte, e, next);
		}

		@Override
		public MozInst exec(MozMachine sc) throws TerminationException {
			if (sc.prefetch() == this.byteChar) {
				sc.consume(1);
				return this.next;
			}
			return sc.fail();
		}
	}

	public static class NByte extends AbstractByteInstruction {
		public NByte(Cbyte e, MozInst next) {
			super(MozSet.NByte, e, next);
		}

		@Override
		public MozInst exec(MozMachine sc) throws TerminationException {
			if (sc.prefetch() != this.byteChar) {
				return this.next;
			}
			return sc.fail();
		}
	}

	public static class OByte extends AbstractByteInstruction {
		public OByte(Cbyte e, MozInst next) {
			super(MozSet.OByte, e, next);
		}

		@Override
		public MozInst exec(MozMachine sc) throws TerminationException {
			if (sc.prefetch() == this.byteChar) {
				sc.consume(1);
			}
			return this.next;
		}
	}

	public static class RByte extends AbstractByteInstruction {
		public RByte(Cbyte e, MozInst next) {
			super(MozSet.RByte, e, next);
		}

		@Override
		public MozInst exec(MozMachine sc) throws TerminationException {
			while (sc.prefetch() == this.byteChar) {
				sc.consume(1);
			}
			return this.next;
		}
	}

	static abstract class AbstractAnyInstruction extends MozInst {
		AbstractAnyInstruction(byte opcode, Expression e, MozInst next) {
			super(opcode, e, next);
		}

		@Override
		protected void encodeImpl(ByteCoder c) {
			// No argument
		}
	}

	public static class Any extends AbstractAnyInstruction {
		public Any(Expression e, MozInst next) {
			super(MozSet.Any, e, next);
		}

		@Override
		public MozInst exec(MozMachine sc) throws TerminationException {
			if (sc.hasUnconsumed()) {
				sc.consume(1);
				return this.next;
			}
			return sc.fail();
		}
	}

	public static class NAny extends AbstractAnyInstruction {
		public NAny(Expression e, boolean isBinary, MozInst next) {
			super(MozSet.NAny, e, next);
		}

		@Override
		public MozInst exec(MozMachine sc) throws TerminationException {
			if (sc.hasUnconsumed()) {
				return sc.fail();
			}
			return next;
		}
	}

	static abstract class AbstractSetInstruction extends MozInst {
		public final boolean[] byteMap;

		AbstractSetInstruction(byte opcode, Cset e, MozInst next) {
			super(opcode, e, next);
			this.byteMap = e.byteMap;
			if (this.byteMap[0]) {
				this.byteMap[0] = false; // for safety
			}
		}

		@Override
		protected String getOperand() {
			return StringUtils.stringfyCharacterClass(byteMap);
		}

		@Override
		protected void encodeImpl(ByteCoder c) {
			c.encodeBset(byteMap);
		}
	}

	public static class Set extends AbstractSetInstruction {
		public Set(Cset e, MozInst next) {
			super(MozSet.Set, e, next);
		}

		@Override
		public MozInst exec(MozMachine sc) throws TerminationException {
			int byteChar = sc.prefetch();
			if (byteMap[byteChar]) {
				sc.consume(1);
				return this.next;
			}
			return sc.fail();
		}
	}

	public static class OSet extends AbstractSetInstruction {
		public OSet(Cset e, MozInst next) {
			super(MozSet.OSet, e, next);
		}

		@Override
		public MozInst exec(MozMachine sc) throws TerminationException {
			int byteChar = sc.prefetch();
			if (byteMap[byteChar]) {
				sc.consume(1);
			}
			return this.next;
		}
	}

	public static class NSet extends AbstractSetInstruction {
		public NSet(Cset e, MozInst next) {
			super(MozSet.NSet, e, next);
		}

		@Override
		public MozInst exec(MozMachine sc) throws TerminationException {
			int byteChar = sc.prefetch();
			if (!byteMap[byteChar]) {
				return this.next;
			}
			return sc.fail();
		}
	}

	public static class RSet extends AbstractSetInstruction {
		public RSet(Cset e, MozInst next) {
			super(MozSet.RSet, e, next);
		}

		@Override
		public MozInst exec(MozMachine sc) throws TerminationException {
			int byteChar = sc.prefetch();
			while (byteMap[byteChar]) {
				sc.consume(1);
				byteChar = sc.prefetch();
			}
			return this.next;
		}
	}

	static abstract class AbstractStrInstruction extends MozInst {
		final byte[] utf8;

		public AbstractStrInstruction(byte opcode, Cmulti e, byte[] utf8, MozInst next) {
			super(opcode, e, next);
			this.utf8 = utf8;
		}

		@Override
		protected String getOperand() {
			StringBuilder sb = new StringBuilder();
			for (int i = 0; i < utf8.length; i++) {
				if (i > 0) {
					sb.append(" ");
				}
				sb.append(StringUtils.stringfyCharacter(utf8[i] & 0xff));
			}
			return sb.toString();
		}

		@Override
		protected void encodeImpl(ByteCoder c) {
			c.encodeBstr(utf8);
		}
	}

	public static class Str extends AbstractStrInstruction {
		public Str(Cmulti e, MozInst next) {
			super(MozSet.Str, e, e.byteSeq, next);
		}

		@Override
		public MozInst exec(MozMachine sc) throws TerminationException {
			if (sc.match(this.utf8)) {
				sc.consume(utf8.length);
				return this.next;
			}
			return sc.fail();
		}
	}

	public static class NStr extends AbstractStrInstruction {
		public NStr(Cmulti e, MozInst next) {
			super(MozSet.NStr, e, e.byteSeq, next);
		}

		@Override
		public MozInst exec(MozMachine sc) throws TerminationException {
			if (!sc.match(this.utf8)) {
				return this.next;
			}
			return sc.fail();
		}
	}

	public static class OStr extends AbstractStrInstruction {
		public OStr(Cmulti e, MozInst next) {
			super(MozSet.OStr, e, e.byteSeq, next);
		}

		@Override
		public MozInst exec(MozMachine sc) throws TerminationException {
			if (sc.match(this.utf8)) {
				sc.consume(utf8.length);
			}
			return this.next;
		}
	}

	public static class RStr extends AbstractStrInstruction {
		public RStr(Cmulti e, MozInst next) {
			super(MozSet.RStr, e, e.byteSeq, next);
		}

		@Override
		public MozInst exec(MozMachine sc) throws TerminationException {
			while (sc.match(this.utf8)) {
				sc.consume(utf8.length);
			}
			return this.next;
		}
	}

	public static class Consume extends MozInst {
		int shift;

		public Consume(Expression e, int shift, MozInst next) {
			super(MozSet.Consume, e, next);
			this.shift = shift;
		}

		@Override
		protected void encodeImpl(ByteCoder c) {
			c.encodeShift(shift);
		}

		@Override
		public MozInst exec(MozMachine sc) throws TerminationException {
			sc.consume(this.shift);
			return this.next;
		}
	}

	// public static class Backtrack extends Instruction {
	// final int prefetched;
	// IBacktrack(Expression e, int prefetched, Instruction next) {
	// super(e, next);
	// this.prefetched = prefetched;
	// }
	// @Override
	// Instruction exec(Context sc) throws TerminationException {
	// sc.consume(-1);
	// return this.next;
	// }
	// }

	public static class First extends MozInst {
		MozInst[] jumpTable;

		public First(byte opcode, Pchoice e, MozInst next) {
			super(opcode, e, next);
			jumpTable = new MozInst[257];
			Arrays.fill(jumpTable, next);
		}

		public First(Pchoice e, MozInst next) {
			this(MozSet.First, e, next);
		}

		void setJumpTable(int ch, MozInst inst) {
			jumpTable[ch] = MozInst.labeling(inst);
		}

		@Override
		protected void encodeImpl(ByteCoder c) {
			c.encodeJumpTable();
			for (int i = 0; i < jumpTable.length; i++) {
				c.encodeJump(jumpTable[i]);
			}
		}

		@Override
		public MozInst exec(MozMachine sc) throws TerminationException {
			int ch = sc.prefetch();
			return jumpTable[ch].exec(sc);
		}
	}

	public static class DFirst extends First {
		public DFirst(Pchoice e, MozInst next) {
			super(MozSet.DFirst, e, next);
		}

		@Override
		public MozInst exec(MozMachine sc) throws TerminationException {
			int ch = sc.prefetch();
			sc.consume(1);
			return jumpTable[ch].exec(sc);
		}
	}

	static abstract class AbstractMemoizationInstruction extends MozInst {
		final MemoPoint memoPoint;
		final int memoId;
		final boolean state;
		final MozInst skip;

		AbstractMemoizationInstruction(byte opcode, Expression e, MemoPoint m, boolean state, MozInst next, MozInst skip) {
			super(opcode, e, next);
			this.memoPoint = m;
			this.memoId = m.id;
			this.skip = labeling(skip);
			this.state = state;
		}

		AbstractMemoizationInstruction(byte opcode, Expression e, MemoPoint m, boolean state, MozInst next) {
			super(opcode, e, next);
			this.memoPoint = m;
			this.memoId = m.id;
			this.state = state;
			this.skip = null;
		}

		@Override
		protected String getOperand() {
			return String.valueOf(this.memoPoint);
		}

		@Override
		protected void encodeImpl(ByteCoder c) {
			c.write_b(this.state);
			c.write_u32(memoId);
			if (skip != null) {
				c.encodeJump(skip);
			}
		}
	}

	public static class Lookup extends AbstractMemoizationInstruction {
		public Lookup(Expression e, MemoPoint m, boolean state, MozInst next, MozInst skip) {
			super(MozSet.Lookup, e, m, state, next, skip);
		}

		@Override
		public MozInst exec(MozMachine sc) throws TerminationException {
			MemoEntry entry = sc.getMemo(memoId, state);
			if (entry != null) {
				if (entry.failed) {
					memoPoint.failHit();
					return sc.fail();
				}
				memoPoint.memoHit(entry.consumed);
				sc.consume(entry.consumed);
				return this.skip;
			}
			memoPoint.miss();
			return this.next;
		}
	}

	public static class Memo extends AbstractMemoizationInstruction {
		public Memo(Expression e, MemoPoint m, boolean state, MozInst next) {
			super(MozSet.Memo, e, m, state, next);
		}

		@Override
		public MozInst exec(MozMachine sc) throws TerminationException {
			long ppos = sc.popAlt();
			int length = (int) (sc.getPosition() - ppos);
			sc.setMemo(ppos, memoId, false, null, length, this.state);
			return this.next;
		}
	}

	public static class MemoFail extends AbstractMemoizationInstruction {
		public MemoFail(Expression e, boolean state, MemoPoint m) {
			super(MozSet.MemoFail, e, m, state, null);
		}

		@Override
		public MozInst exec(MozMachine sc) throws TerminationException {
			sc.setMemo(sc.getPosition(), memoId, true, null, 0, state);
			return sc.fail();
		}
	}

	// AST Construction

	public static class New extends MozInst {
		int shift;

		public New(Tnew e, MozInst next) {
			super(MozSet.TNew, e, next);
			this.shift = e.shift;
		}

		@Override
		protected void encodeImpl(ByteCoder c) {
			c.encodeShift(shift);
		}

		@Override
		public MozInst exec(MozMachine sc) throws TerminationException {
			ASTMachine astMachine = sc.getAstMachine();
			astMachine.logNew(sc.getPosition() + shift, this.id);
			return this.next;
		}
	}

	public static class TLeftFold extends MozInst {
		int shift;
		Symbol label;

		public TLeftFold(Tlfold e, MozInst next) {
			super(MozSet.TLeftFold, e, next);
			this.shift = e.shift;
			this.label = e.getLabel();
		}

		@Override
		protected void encodeImpl(ByteCoder c) {
			c.encodeShift(shift);
			c.encodeLabel(label);
		}

		@Override
		public MozInst exec(MozMachine sc) throws TerminationException {
			ASTMachine astMachine = sc.getAstMachine();
			astMachine.logLeftFold(sc.getPosition() + shift, this.label);
			return this.next;
		}
	}

	public static class Capture extends MozInst {
		int shift;

		public Capture(Tcapture e, MozInst next) {
			super(MozSet.TCapture, e, next);
			this.shift = e.shift;
		}

		@Override
		protected void encodeImpl(ByteCoder c) {
			c.encodeShift(shift);
		}

		@Override
		public MozInst exec(MozMachine sc) throws TerminationException {
			ASTMachine astMachine = sc.getAstMachine();
			astMachine.logCapture(sc.getPosition() + shift);
			return this.next;
		}
	}

	public static class Replace extends MozInst {
		public final String value;

		public Replace(Treplace e, MozInst next) {
			super(MozSet.TReplace, e, next);
			this.value = e.value;
		}

		@Override
		protected String getOperand() {
			return StringUtils.quoteString('"', value, '"');
		}

		@Override
		protected void encodeImpl(ByteCoder c) {
			c.encodeBstr(value.getBytes());
		}

		@Override
		public MozInst exec(MozMachine sc) throws TerminationException {
			ASTMachine astMachine = sc.getAstMachine();
			astMachine.logReplace(this.value);
			return this.next;
		}
	}

	public static class Tag extends MozInst {
		public final Symbol tag;

		public Tag(Ttag e, MozInst next) {
			super(MozSet.TTag, e, next);
			this.tag = e.tag;
		}

		@Override
		protected String getOperand() {
			return StringUtils.quoteString('"', tag.getSymbol(), '"');
		}

		@Override
		protected void encodeImpl(ByteCoder c) {
			c.encodeTag(tag);
		}

		@Override
		public MozInst exec(MozMachine sc) throws TerminationException {
			ASTMachine astMachine = sc.getAstMachine();
			astMachine.logTag(tag);
			return this.next;
		}
	}

	public static class TPush extends MozInst {
		public TPush(Tlink e, MozInst next) {
			super(MozSet.TPush, e, next);
		}

		@Override
		protected void encodeImpl(ByteCoder c) {
			// No argument
		}

		@Override
		public MozInst exec(MozMachine sc) throws TerminationException {
			ASTMachine astMachine = sc.getAstMachine();
			astMachine.logPush();
			return this.next;
		}
	}

	public static class TPop extends MozInst {
		public final Symbol label;

		public TPop(Tlink e, MozInst next) {
			super(MozSet.TPop, e, next);
			this.label = e.getLabel();
		}

		@Override
		protected String getOperand() {
			return label.getSymbol();
		}

		@Override
		protected void encodeImpl(ByteCoder c) {
			c.encodeLabel(label);
		}

		@Override
		public MozInst exec(MozMachine sc) throws TerminationException {
			ASTMachine astMachine = sc.getAstMachine();
			astMachine.logPop(label);
			return this.next;
		}
	}

	public static class TStart extends MozInst {
		public TStart(Tlink e, MozInst next) {
			super(MozSet.TStart, e, next);
		}

		@Override
		protected void encodeImpl(ByteCoder c) {
			// No argument
		}

		@Override
		public MozInst exec(MozMachine sc) throws TerminationException {
			StackData s = sc.newUnusedStack();
			ASTMachine astMachine = sc.getAstMachine();
			s.ref = astMachine.saveTransactionPoint();
			return this.next;
		}
	}

	public static class Commit extends MozInst {
		public final Symbol label;

		public Commit(Tlink e, MozInst next) {
			super(MozSet.TCommit, e, next);
			this.label = e.getLabel();
		}

		@Override
		protected void encodeImpl(ByteCoder c) {
			c.encodeLabel(label);
		}

		@Override
		public MozInst exec(MozMachine sc) throws TerminationException {
			StackData s = sc.popStack();
			ASTMachine astMachine = sc.getAstMachine();
			astMachine.commitTransactionPoint(label, s.ref);
			return this.next;
		}
	}

	public static class TLookup extends AbstractMemoizationInstruction {
		public final Symbol label;

		public TLookup(Tlink e, MemoPoint m, boolean state, MozInst next, MozInst skip) {
			super(MozSet.TLookup, e, m, state, next, skip);
			this.label = e.getLabel();
		}

		@Override
		protected void encodeImpl(ByteCoder c) {
			super.encodeImpl(c);
			c.encodeLabel(label);
		}

		@Override
		public MozInst exec(MozMachine sc) throws TerminationException {
			MemoEntry entry = sc.getMemo(memoId, state);
			if (entry != null) {
				if (entry.failed) {
					memoPoint.failHit();
					return sc.fail();
				}
				memoPoint.memoHit(entry.consumed);
				sc.consume(entry.consumed);
				ASTMachine astMachine = sc.getAstMachine();
				astMachine.logLink(label, entry.result);
				return this.skip;
			}
			memoPoint.miss();
			return this.next;
		}
	}

	public static class TMemo extends AbstractMemoizationInstruction {
		public TMemo(Expression e, MemoPoint m, boolean state, MozInst next) {
			super(MozSet.TMemo, e, m, state, next);
		}

		@Override
		public MozInst exec(MozMachine sc) throws TerminationException {
			ASTMachine astMachine = sc.getAstMachine();
			long ppos = sc.popAlt();
			int length = (int) (sc.getPosition() - ppos);
			sc.setMemo(ppos, memoId, false, astMachine.getLatestLinkedNode(), length, this.state);
			return this.next;
		}
	}

	/* Symbol */

	static abstract class AbstractTableInstruction extends MozInst {
		final Symbol tableName;

		AbstractTableInstruction(byte opcode, Expression e, Symbol tableName, MozInst next) {
			super(opcode, e, next);
			this.tableName = tableName;
		}

		@Override
		protected String getOperand() {
			return tableName.getSymbol();
		}

		@Override
		protected void encodeImpl(ByteCoder c) {
			c.encodeTable(tableName);
		}
	}

	public static class BeginSymbolScope extends MozInst {
		public BeginSymbolScope(Xblock e, MozInst next) {
			super(MozSet.SOpen, e, next);
		}

		@Override
		protected void encodeImpl(ByteCoder c) {
			// No Arguments
		}

		@Override
		public MozInst exec(MozMachine sc) throws TerminationException {
			StackData s = sc.newUnusedStack();
			s.value = sc.getSymbolTable().savePoint();
			return this.next;
		}
	}

	public static class BeginLocalScope extends AbstractTableInstruction {
		public BeginLocalScope(Xlocal e, MozInst next) {
			super(MozSet.SMask, e, e.getTable(), next);
		}

		@Override
		public MozInst exec(MozMachine sc) throws TerminationException {
			StackData s = sc.newUnusedStack();
			SymbolTable st = sc.getSymbolTable();
			s.value = st.savePoint();
			st.addSymbolMask(tableName);
			return this.next;
		}
	}

	public static class EndSymbolScope extends MozInst {
		public EndSymbolScope(Expression e, MozInst next) {
			super(MozSet.SClose, e, next);
		}

		@Override
		protected void encodeImpl(ByteCoder c) {
		}

		@Override
		public MozInst exec(MozMachine sc) throws TerminationException {
			StackData s = sc.popStack();
			sc.getSymbolTable().rollBack((int) s.value);
			return this.next;
		}
	}

	public static class DefSymbol extends AbstractTableInstruction {
		public DefSymbol(Xsymbol e, MozInst next) {
			super(MozSet.SDef, e, e.tableName, next);
		}

		@Override
		public MozInst exec(MozMachine sc) throws TerminationException {
			StackData top = sc.popStack();
			byte[] captured = sc.subbyte(top.value, sc.getPosition());
			// System.out.println("symbol captured: " + new String(captured) +
			// ", @"
			// + this.tableName);
			sc.getSymbolTable().addSymbol(this.tableName, captured);
			return this.next;
		}
	}

	public static class Exists extends AbstractTableInstruction {
		public Exists(Xexists e, MozInst next) {
			super(MozSet.SExists, e, e.tableName, next);
		}

		@Override
		public MozInst exec(MozMachine sc) throws TerminationException {
			byte[] t = sc.getSymbolTable().getSymbol(tableName);
			return t != null ? this.next : sc.fail();
		}
	}

	public static class ExistsSymbol extends AbstractTableInstruction {
		byte[] symbol;

		public ExistsSymbol(Xexists e, MozInst next) {
			super(MozSet.SIsDef, e, e.tableName, next);
			symbol = StringUtils.toUtf8(e.getSymbol());
		}

		@Override
		protected void encodeImpl(ByteCoder c) {
			super.encodeImpl(c);
			c.encodeBstr(symbol);
		}

		@Override
		public MozInst exec(MozMachine sc) throws TerminationException {
			if (sc.getSymbolTable().contains(this.tableName, symbol)) {
				return this.next;
			}
			return sc.fail();
		}
	}

	public static class Match extends AbstractTableInstruction {
		public Match(Xmatch e, MozInst next) {
			super(MozSet.SMatch, e, e.getTable(), next);
		}

		@Override
		public MozInst exec(MozMachine sc) throws TerminationException {
			byte[] t = sc.getSymbolTable().getSymbol(tableName);
			if (t == null) {
				return this.next;
			}
			if (sc.match(t)) {
				sc.consume(t.length);
				return this.next;
			}
			return sc.fail();
		}
	}

	public static class IsSymbol extends AbstractTableInstruction {
		public IsSymbol(Xis e, MozInst next) {
			super(MozSet.SIs, e, e.tableName, next);
		}

		@Override
		public MozInst exec(MozMachine sc) throws TerminationException {
			byte[] symbol = sc.getSymbolTable().getSymbol(tableName);
			if (symbol != null) {
				StackData s = sc.popStack();
				byte[] captured = sc.subbyte(s.value, sc.getPosition());
				// System.out.println("captured:" + new String(captured));
				if (symbol.length == captured.length && SymbolTable.equals(symbol, captured)) {
					// sc.consume(symbol.length);
					return this.next;
				}
			}
			return sc.fail();
		}
	}

	public static class IsaSymbol extends AbstractTableInstruction {
		public IsaSymbol(Xis e, MozInst next) {
			super(MozSet.SIsa, e, e.tableName, next);
		}

		@Override
		public MozInst exec(MozMachine sc) throws TerminationException {
			StackData s = sc.popStack();
			byte[] captured = sc.subbyte(s.value, sc.getPosition());
			if (sc.getSymbolTable().contains(this.tableName, captured)) {
				// sc.consume(captured.length);
				return this.next;

			}
			return sc.fail();
		}
	}

	// public static class DefIndent extends MozInst {
	// public final static Symbol _Indent = Symbol.tag("Indent");
	//
	// public DefIndent(Xdefindent e, MozInst next) {
	// super(MozSet.Nop, e, next);
	// }
	//
	// final long getLineStartPosition(MozMachine sc, long fromPostion) {
	// long startIndex = fromPostion;
	// if (!(startIndex < sc.length())) {
	// startIndex = sc.length() - 1;
	// }
	// if (startIndex < 0) {
	// startIndex = 0;
	// }
	// while (startIndex > 0) {
	// int ch = sc.byteAt(startIndex);
	// if (ch == '\n') {
	// startIndex = startIndex + 1;
	// break;
	// }
	// startIndex = startIndex - 1;
	// }
	// return startIndex;
	// }
	//
	// @Override
	// protected void encodeImpl(ByteCoder c) {
	// // No argument
	// }
	//
	// @Override
	// public MozInst exec(MozMachine sc) throws TerminationException {
	// long pos = sc.getPosition();
	// long spos = getLineStartPosition(sc, pos);
	// byte[] b = sc.subbyte(spos, pos);
	// for (int i = 0; i < b.length; i++) {
	// if (b[i] != '\t') {
	// b[i] = ' ';
	// }
	// }
	// sc.getSymbolTable().addSymbol(_Indent, b);
	// return this.next;
	// }
	// }
	//
	// public static class IsIndent extends MozInst {
	// public final static Symbol _Indent = Symbol.tag("Indent");
	//
	// public IsIndent(Xindent e, MozInst next) {
	// super(MozSet.Nop, e, next);
	// }
	//
	// @Override
	// protected void encodeImpl(ByteCoder c) {
	// // No argument
	// }
	//
	// @Override
	// public MozInst exec(MozMachine sc) throws TerminationException {
	// long pos = sc.getPosition();
	// if (pos > 0) {
	// if (sc.byteAt(pos - 1) != '\n') {
	// return sc.fail();
	// }
	// }
	// byte[] b = sc.getSymbolTable().getSymbol(_Indent);
	// if (b != null) {
	// if (sc.match(b)) {
	// sc.consume(b.length);
	// return this.next;
	// }
	// return sc.fail();
	// }
	// return this.next; // empty entry is allowable
	// }
	// }

}