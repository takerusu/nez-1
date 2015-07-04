package nez.debugger;

import nez.lang.AnyChar;
import nez.lang.ByteChar;
import nez.lang.ByteMap;
import nez.lang.Expression;
import nez.lang.NonTerminal;

public class IRBuilder {
	private BasicBlock curBB;
	private Module module;
	private Function func;

	public IRBuilder(Module m) {
		this.module = m;
	}

	public Module buildInstructionSequence() {
		int codeIndex = 0;
		for(int i = 0; i < this.module.size(); i++) {
			Function func = this.module.get(i);
			for(int j = 0; j < func.size(); j++) {
				BasicBlock bb = func.get(j);
				bb.codePoint = codeIndex;
				codeIndex += bb.size();
			}
		}
		for(Function f : this.module.funcList) {
			DebugVMInstruction prev = null;
			for(BasicBlock bb : f.bbList) {
				for(DebugVMInstruction inst : bb.insts) {
					if(prev != null) {
						prev.next = inst;
					}
					if(inst instanceof JumpInstruction) {
						JumpInstruction jinst = (JumpInstruction) inst;
						BasicBlock jbb = jinst.jumpBB;
						if(jbb.size() != 0) {
							jinst.jump = jinst.jumpBB.get(0);
						} else {
							while(jbb.size() == 0) {
								jbb = jbb.getSingleSuccessor();
							}
							jinst.jump = jbb.get(0);
						}
					}
					prev = inst;
				}
			}
		}
		this.dumpLastestCode();
		return this.module;
	}

	private void dumpLastestCode() {
		int codeIndex = 0;
		for(int i = 0; i < this.module.size(); i++) {
			Function func = this.module.get(i);
			for(int j = 0; j < func.size(); j++) {
				BasicBlock bb = func.get(j);
				for(int k = 0; k < bb.size(); k++) {
					DebugVMInstruction inst = bb.get(k);
					System.out.println("[" + codeIndex + "] " + inst.toString());
					codeIndex++;
				}
			}
		}
	}

	public Module getModule() {
		return this.module;
	}

	public Function getFunction() {
		return this.func;
	}

	public void setFunction(Function func) {
		this.module.append(func);
		this.func = func;
	}

	public void setInsertPoint(BasicBlock bb) {
		this.func.append(bb);
		bb.setName("bb" + this.func.size());
		if(this.curBB != null) {
			if(bb.size() != 0) {
				DebugVMInstruction last = this.curBB.get(this.curBB.size() - 1);
				if(!(last.op.equals(Opcode.Ijump) || last.op.equals(Opcode.Icall))) {
					this.curBB.setSingleSuccessor(bb);
				}
			} else {
				this.curBB.setSingleSuccessor(bb);
			}
		}
		this.curBB = bb;
	}

	public void setCurrentBB(BasicBlock bb) {
		this.curBB = bb;
	}

	public BasicBlock getCurrentBB() {
		return this.curBB;
	}

	class FailureBB {
		BasicBlock fbb;
		FailureBB prev;

		public FailureBB(BasicBlock bb, FailureBB prev) {
			this.fbb = bb;
			this.prev = prev;
		}
	}

	FailureBB fLabel = null;

	public void pushFailureJumpPoint(BasicBlock bb) {
		this.fLabel = new FailureBB(bb, this.fLabel);
	}

	public BasicBlock popFailureJumpPoint() {
		BasicBlock fbb = this.fLabel.fbb;
		this.fLabel = this.fLabel.prev;
		return fbb;
	}

	public BasicBlock jumpFailureJump() {
		return this.fLabel.fbb;
	}

	public BasicBlock jumpPrevFailureJump() {
		return this.fLabel.prev.fbb;
	}

	public DebugVMInstruction createIexit(Expression e) {
		return this.curBB.append(new Iexit(e));
	}

	public DebugVMInstruction createIcall(NonTerminal e, BasicBlock jump, BasicBlock failjump) {
		return this.curBB.append(new Icall(e, jump, failjump));
	}

	public DebugVMInstruction createIret(Expression e) {
		return this.curBB.append(new Iret(e));
	}

	public DebugVMInstruction createIjump(Expression e, BasicBlock jump) {
		return this.curBB.append(new Ijump(e, jump));
	}

	public DebugVMInstruction createIiffail(Expression e, BasicBlock jump) {
		return this.curBB.append(new Iiffail(e, jump));
	}

	public DebugVMInstruction createIpush(Expression e) {
		return this.curBB.append(new Ipush(e));
	}

	public DebugVMInstruction createIpop(Expression e) {
		return this.curBB.append(new Ipop(e));
	}

	public DebugVMInstruction createIpeek(Expression e) {
		return this.curBB.append(new Ipeek(e));
	}

	public DebugVMInstruction createIsucc(Expression e) {
		return this.curBB.append(new Isucc(e));
	}

	public DebugVMInstruction createIfail(Expression e) {
		return this.curBB.append(new Ifail(e));
	}

	public DebugVMInstruction createIchar(ByteChar e, BasicBlock jump) {
		return this.curBB.append(new Ichar(e, jump));
	}

	public DebugVMInstruction createIcharclass(ByteMap e, BasicBlock jump) {
		return this.curBB.append(new Icharclass(e, jump));
	}

	public DebugVMInstruction createIany(AnyChar e, BasicBlock jump) {
		return this.curBB.append(new Iany(e, jump));
	}
}
