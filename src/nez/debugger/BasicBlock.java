package nez.debugger;

import java.util.ArrayList;
import java.util.List;

public class BasicBlock {
	String name;
	int codePoint;
	List<Instruction> insts;
	List<BasicBlock> preds;
	List<BasicBlock> succs;

	public BasicBlock() {
		this.insts = new ArrayList<Instruction>();
	}

	public Instruction get(int index) {
		return this.insts.get(index);
	}

	public Instruction append(Instruction inst) {
		this.insts.add(inst);
		return inst;
	}

	public BasicBlock add(int index, Instruction inst) {
		this.insts.add(index, inst);
		return this;
	}

	public Instruction remove(int index) {
		return this.insts.remove(index);
	}

	public int size() {
		return this.insts.size();
	}

	public int indexOf(Instruction inst) {
		return this.insts.indexOf(inst);
	}

	public void stringfy(StringBuilder sb) {
		for(int i = 0; i < this.size(); i++) {
			this.get(i).stringfy(sb);
			sb.append("\n");
		}
	}

	public String getName() {
		return this.name;
	}

	public List<BasicBlock> getPredecessors() {
		return this.preds;
	}

	public List<BasicBlock> getSuccessors() {
		return this.succs;
	}

	public BasicBlock getSingleSuccessor() {
		return this.succs.get(0);
	}

	public BasicBlock getFailSuccessor() {
		return this.succs.get(1);
	}
}
