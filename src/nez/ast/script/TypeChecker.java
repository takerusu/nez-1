package nez.ast.script;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;

import nez.ast.Symbol;
import nez.ast.TreeVisitor;
import nez.ast.script.TypeSystem.BinaryTypeUnifier;
import nez.util.StringUtils;
import nez.util.UList;

public class TypeChecker extends TreeVisitor implements CommonSymbols {
	boolean isShellMode = true;
	ScriptContext context;
	TypeSystem typeSystem;

	// TypeScope scope;

	public TypeChecker(ScriptContext context, TypeSystem typeSystem) {
		super(TypedTree.class);
		this.context = context;
		this.typeSystem = typeSystem;
	}

	FunctionBuilder function = null;

	//
	// boolean inFunction = false;
	// Type returnType = null;

	public final FunctionBuilder enterFunction(String name) {
		this.function = new FunctionBuilder(this.function, name);
		return this.function;
	}

	public final void exitFunction() {
		this.function = this.function.pop();
	}

	public final boolean inFunction() {
		return this.function != null;
	}

	public Type type(TypedTree node) {
		Type c = (Type) visit("type", node);
		if (c != null) {
			node.setType(c);
		}
		return c;
	}

	private String name(Type t) {
		return this.typeSystem.name(t);
	}

	public void enforceType(Type req, TypedTree node, Symbol label) {
		TypedTree unode = node.get(label, null);
		if (unode == null) {
			throw this.error(node, "syntax error: %s is required", label);
		}
		type(unode);
		node.set(label, this.typeSystem.enforceType(req, unode));
	}

	public void typed(TypedTree node, Type c) {
		node.setType(c);
	}

	/* TopLevel */

	public Type typeSource(TypedTree node) {
		Type t = null;
		for (int i = 0; i < node.size(); i++) {
			TypedTree sub = node.get(i);
			try {
				t = type(sub);
			} catch (TypeCheckerException e) {
				sub = e.errorTree;
				node.set(i, sub);
				t = sub.getType();
			}
		}
		return t;
	}

	public Type typeImport(TypedTree node) {
		StringBuilder sb = new StringBuilder();
		join(sb, node.get(0)); // FIXME: konoha.nez
		String path = sb.toString();
		try {
			typeSystem.importStaticClass(path);
		} catch (ClassNotFoundException e) {
			throw error(node, "undefined class name: %s", path);
		}
		node.done();
		return void.class;
	}

	private void join(StringBuilder sb, TypedTree node) {
		TypedTree prefix = node.get(_prefix);
		if (prefix.size() == 2) {
			join(sb, prefix);
		} else {
			sb.append(prefix.toText());
		}
		sb.append(".").append(node.getText(_name, null));
	}

	/* FuncDecl */
	private static Type[] EmptyTypes = new Type[0];

	public Type typeFuncDecl(TypedTree node) {
		String name = node.getText(_name, null);
		TypedTree bodyNode = node.get(_body, null);
		Type returnType = typeSystem.resolveType(node.get(_type, null), null);
		Type[] paramTypes = EmptyTypes;
		TypedTree params = node.get(_param, null);
		if (node.has(_param)) {
			int c = 0;
			paramTypes = new Type[params.size()];
			for (TypedTree p : params) {
				paramTypes[c] = typeSystem.resolveType(p.get(_type, null), Object.class);
				c++;
			}
		}
		/* prototye declration */
		if (bodyNode == null) {
			Class<?> funcType = this.typeSystem.getFuncType(returnType, paramTypes);
			typeSystem.newGlobalVariable(funcType, name);
			node.done();
			return void.class;
		}
		FunctionBuilder f = this.enterFunction(name);
		if (returnType != null) {
			f.setReturnType(returnType);
			typed(node.get(_type), returnType);
		}
		if (node.has(_param)) {
			int c = 0;
			for (TypedTree sub : params) {
				String pname = sub.getText(_name, null);
				f.setVarType(pname, paramTypes[c]);
				typed(sub, paramTypes[c]);
			}
		}
		try {
			type(bodyNode);
		} catch (TypeCheckerException e) {
			node.set(_body, e.errorTree);
		}
		this.exitFunction();
		if (f.getReturnType() == null) {
			f.setReturnType(void.class);
		}
		typed(node.get(_name), f.getReturnType());
		return void.class;
	}

	public Type typeReturn(TypedTree node) {
		if (!inFunction()) {
			throw this.error(node, "return must be inside function");
		}
		Type t = this.function.getReturnType();
		if (t == null) { // type inference
			if (node.has(_expr)) {
				this.function.setReturnType(type(node.get(_expr)));
			} else {
				this.function.setReturnType(void.class);
			}
			return void.class;
		}
		if (t == void.class) {
			if (node.size() > 0) {
				node.removeSubtree();
			}
		} else {
			this.enforceType(t, node, _expr);
		}
		return void.class;
	}

	/* Statement */

	public Type typeBlock(TypedTree node) {
		if (inFunction()) {
			this.function.beginLocalVarScope();
		}
		typeStatementList(node);
		if (inFunction()) {
			this.function.endLocalVarScope();
		}
		return void.class;
	}

	public Type typeStatementList(TypedTree node) {
		for (int i = 0; i < node.size(); i++) {
			TypedTree sub = node.get(i);
			try {
				type(sub);
			} catch (TypeCheckerException e) {
				sub = e.errorTree;
				node.set(i, sub);
			}
		}
		return void.class;
	}

	public Type typeIf(TypedTree node) {
		this.enforceType(boolean.class, node, _cond);
		type(node.get(_then));
		if (node.get(_else, null) != null) {
			type(node.get(_else));
		}
		return void.class;
	}

	public Type typeConditional(TypedTree node) {
		this.enforceType(boolean.class, node, _cond);
		Type then_t = type(node.get(_then));
		Type else_t = type(node.get(_else));
		if (then_t != else_t) {
			this.enforceType(then_t, node, _else);
		}
		return then_t;
	}

	public Type typeWhile(TypedTree node) {
		this.enforceType(boolean.class, node, _cond);
		type(node.get(_body));
		return void.class;
	}

	public Type typeContinue(TypedTree node) {
		return void.class;
	}

	public Type typeBreak(TypedTree node) {
		return void.class;
	}

	public Type typeFor(TypedTree node) {
		if (inFunction()) {
			this.function.beginLocalVarScope();
		}
		if (node.has(_init)) {
			type(node.get(_init));
		}
		if (node.has(_cond)) {
			this.enforceType(boolean.class, node, _cond);
		}
		if (node.has(_iter)) {
			type(node.get(_iter));
		}
		type(node.get(_body));
		if (inFunction()) {
			this.function.endLocalVarScope();
		}
		return void.class;
	}

	public Type typeForEach(TypedTree node) {
		Type req_t = null;
		if (node.has(_type)) {
			req_t = this.typeSystem.resolveType(node.get(_type), null);
		}
		String name = node.getText(_name, "");
		req_t = typeIterator(req_t, node.get(_iter));
		if (inFunction()) {
			this.function.beginLocalVarScope();
		}
		this.function.setVarType(name, req_t);
		type(node.get(_body));
		if (inFunction()) {
			this.function.endLocalVarScope();
		}
		return void.class;
	}

	protected Type[] EmptyArgument = new Type[0];

	private Type typeIterator(Type req_t, TypedTree node) {
		Type iter_t = type(node.get(_iter));
		Method m = typeSystem.resolveObjectMethod(req_t, this.bufferMatcher, "iterator", EmptyArgument, null, null);
		if (m != null) {
			TypedTree iter = node.newInstance(_MethodApply, 0, null);
			iter.make(_recv, node.get(_iter), _param, node.newInstance(_List, 0, null));
			iter_t = iter.setMethod(Hint.MethodApply, m, this.bufferMatcher);
			// TODO
			// if(req_t != null) {
			// }
			// node.set(index, node)
		}
		throw error(node.get(_iter), "unsupported iterator for %s", name(iter_t));
	}

	public Type typeVarDecl(TypedTree node) {
		String name = node.getText(_name, null);
		Type type = typeSystem.resolveType(node.get(_type, null), null);
		TypedTree exprNode = node.get(_expr, null);
		if (type == null) {
			if (exprNode == null) {
				this.typeSystem.reportWarning(node.get(_name), "ungiven type");
				type = Object.class;
			} else {
				type = type(exprNode);
			}
		} else {
			if (exprNode != null) {
				enforceType(type, node, _expr);
			}
		}
		typed(node.get(_name), type);
		if (this.inFunction()) {
			// System.out.println("local variable");
			this.function.setVarType(name, type);
			if (exprNode == null) {
				node.done();
				return void.class;
			}
			// Assign
			node.rename(_VarDecl, _Assign);
			node.rename(_name, _left);
			node.rename(_expr, _right);
		} else {
			// System.out.println("global variable");
			GlobalVariable gv = typeSystem.getGlobalVariable(name);
			if (gv != null) {
				if (gv.getType() != type) {
					throw error(node.get(_name), "already defined name: %s as %s", name, name(gv.getType()));
				}
			} else {
				gv = typeSystem.newGlobalVariable(type, name);
			}
			if (exprNode == null) {
				node.done();
				return void.class;
			}
			// Assign
			node.rename(_VarDecl, _Assign);
			return node.setField(Hint.SetField, gv.field);
		}
		return void.class;
	}

	/* StatementExpression */
	public Type typeExpression(TypedTree node) {
		return type(node.get(0));
	}

	/* Expression */

	public Type typeName(TypedTree node) {
		Type t = this.tryCheckNameType(node, true);
		if (t == null) {
			String name = node.toText();
			throw error(node, "undefined name: %s", name);
		}
		return t;
	}

	private Type tryCheckNameType(TypedTree node, boolean rewrite) {
		String name = node.toText();
		if (this.inFunction()) {
			if (this.function.containsVariable(name)) {
				return this.function.getVarType(name);
			}
		}
		if (this.typeSystem.hasGlobalVariable(name)) {
			GlobalVariable gv = this.typeSystem.getGlobalVariable(name);
			if (rewrite) {
				node.setField(Hint.GetField, gv.field);
			}
			return gv.getType();
		}
		return null;
	}

	public Type typeAssign(TypedTree node) {
		TypedTree leftnode = node.get(_left);
		if (isShellMode && !this.inFunction() && leftnode.is(_Name)) {
			String name = node.getText(_left, null);
			if (!this.typeSystem.hasGlobalVariable(name)) {
				this.typeSystem.newGlobalVariable(Object.class, name);
			}
		}
		if (leftnode.is(_Indexer)) {
			return typeSetIndexer(node, //
					node.get(_left).get(_recv), //
					node.get(_left).get(_param), //
					node.get(_right));
		}
		Type left = type(leftnode);
		this.enforceType(left, node, _right);

		if (leftnode.hint == Hint.GetField) {
			Field f = leftnode.getField();
			if (Modifier.isFinal(f.getModifiers())) {
				throw error(node.get(_left), "readonly");
			}
			if (!Modifier.isStatic(f.getModifiers())) {
				node.set(_left, leftnode.get(_recv));
				node.rename(_left, _recv);
			}
			node.rename(_right, _expr);
			node.setField(Hint.SetField, f);
		}
		return left;
	}

	/* Expression */

	public Type typeCast(TypedTree node) {
		Type inner = type(node.get(_expr));
		Type t = this.typeSystem.resolveType(node.get(_type), null);
		if (t == null) {
			throw error(node.get(_type), "undefined type: %s", node.getText(_type, ""));
		}
		Class<?> req = TypeSystem.toClass(t);
		Class<?> exp = TypeSystem.toClass(inner);
		Method m = typeSystem.getCastMethod(exp, req);
		if (m == null) {
			m = typeSystem.getConvertMethod(exp, req);
		}
		if (m != null) {
			node.makeFlattenedList(node.get(_expr));
			return node.setMethod(Hint.StaticInvocation, m, null);
		}
		if (req.isAssignableFrom(exp)) { // upcast
			node.setTag(_UpCast);
			return t;
		}
		if (exp.isAssignableFrom(req)) { // downcast
			node.setTag(_DownCast);
			return t;
		}
		throw error(node.get(_type), "undefined cast: %s => %s", name(inner), name(t));
	}

	// public Type[] typeList(TypedTree node) {
	// Type[] args = new Type[node.size()];
	// for (int i = 0; i < node.size(); i++) {
	// args[i] = type(node.get(i));
	// }
	// return args;
	// }

	public Type typeField(TypedTree node) {
		if (isStaticClassRecv(node)) {
			return typeStaticField(node);
		}
		Class<?> c = TypeSystem.toClass(type(node.get(_recv)));
		String name = node.getText(_name, "");
		Field f = typeSystem.getField(c, name);
		if (f != null) {
			return node.setField(Hint.GetField, f);
		}
		if (typeSystem.isDynamic(c)) {
			return node.setMethod(Hint.StaticInvocation, typeSystem.DynamicGetter, null);
		}
		throw error(node.get(_name), "undefined field %s of %s", name, name(c));
	}

	public Type typeStaticField(TypedTree node) {
		Class<?> c = this.typeSystem.resolveClass(node.get(_recv), null);
		String name = node.getText(_name, "");
		Field f = typeSystem.getField(c, name);
		if (f != null) {
			if (!Modifier.isStatic(f.getModifiers())) {
				throw error(node, "not static field %s of %s", name, name(c));
			}
			return node.setField(Hint.GetField, f);
		}
		throw error(node.get(_name), "undefined field %s of %s", name, name(c));
	}

	public Type typeIndexer(TypedTree node) {
		Type recv_t = type(node.get(_recv));
		Type[] param_t = this.typeApplyArguments(node.get(_param));
		int start = this.bufferMethods.size();
		Method m = this.typeSystem.resolveObjectMethod(recv_t, this.bufferMatcher, "get", param_t, null, null);
		if (m != null) {
			return this.resolvedMethod(node, Hint.MethodApply, m, bufferMatcher);
		}
		if (this.typeSystem.isDynamic(recv_t)) {
			node.makeFlattenedList(node.get(_recv), node.get(_param));
			return node.setMethod(Hint.StaticInvocation, typeSystem.ObjectIndexer, null);
		}
		return this.undefinedMethod(node, start, "unsupported indexer [] for %s", name(recv_t));
	}

	private Type typeSetIndexer(TypedTree node, TypedTree recv, TypedTree param, TypedTree expr) {
		param.makeFlattenedList(param, expr);
		node.make(_recv, recv, _param, param);
		Type recv_t = type(node.get(_recv));
		Type[] param_t = this.typeApplyArguments(node.get(_param));
		int start = this.bufferMethods.size();
		this.bufferMatcher.init(recv_t);
		Method m = this.typeSystem.resolveObjectMethod(recv_t, this.bufferMatcher, "set", param_t, this.bufferMethods, node.get(_param));
		if (m != null) {
			return this.resolvedMethod(node, Hint.MethodApply, m, bufferMatcher);
		}
		if (this.typeSystem.isDynamic(recv_t)) {
			node.makeFlattenedList(node.get(_recv), node.get(_param));
			return node.setMethod(Hint.StaticInvocation, typeSystem.ObjectSetIndexer, null);
		}
		return this.undefinedMethod(node, start, "unsupported set indexer [] for %s", name(recv_t));
	}

	TypeVarMatcher bufferMatcher = new TypeVarMatcher();
	UList<Method> bufferMethods = new UList<Method>(new Method[128]);

	private String methods(UList<Method> bufferMethods, int start) {
		StringBuilder sb = new StringBuilder();
		for (int i = start; i < bufferMethods.size(); i++) {
			sb.append(" ");
			sb.append(bufferMethods.ArrayValues[i]);
		}
		bufferMethods.clear(start);
		return sb.toString();
	}

	private Type resolvedMethod(TypedTree node, Hint hint, Method m, TypeVarMatcher matcher) {
		return node.setMethod(hint, m, matcher);
	}

	private Type undefinedMethod(TypedTree node, int start, String fmt, Object... args) {
		String msg = String.format(fmt, args);
		if (this.bufferMethods.size() > start) {
			msg = "mismatched " + msg + methods(bufferMethods, start);
		} else {
			msg = "undefined " + msg;
		}
		throw error(node, msg);
	}

	public Type typeApply(TypedTree node) {
		String name = node.getText(_name, "");
		TypedTree args = node.get(_param);
		Type[] types = typeApplyArguments(args);
		if (isRecursiveCall(name, types)) {
			return typeRecursiveApply(node, name, types);
		}
		Type func_t = this.tryCheckNameType(node.get(_name), true);
		if (this.typeSystem.isFuncType(func_t)) {
			return typeFuncApply(node, func_t, types, args);
		}
		int start = this.bufferMethods.size();
		Method m = this.typeSystem.resolveFunctionMethod(name, types, bufferMethods, args);
		return m != null ? this.resolvedMethod(node, Hint.Apply, m, null) //
				: this.undefinedMethod(node, start, "funciton: %s", name);
	}

	private Type[] typeApplyArguments(TypedTree args) {
		Type[] types = new Type[args.size()];
		for (int i = 0; i < args.size(); i++) {
			types[i] = type(args.get(i));
		}
		return types;
	}

	private boolean isRecursiveCall(String name, Type[] params_t) {
		if (inFunction()) {
			return false; // this.function.match(name, params_t);
		}
		return false;
	}

	private Type typeRecursiveApply(TypedTree node, String name, Type[] params_t) {
		throw error(node, "unsupported recursive call: %s", name);
	}

	private Type typeFuncApply(TypedTree node, Type func_t, Type[] params_t, TypedTree params) {
		Method m = Reflector.getInvokeFunctionMethod(params.size());
		if (m != null) {
			for (int i = 0; i < params.size(); i++) {
				params.set(i, this.typeSystem.enforceType(Object.class, params.get(i)));
			}
			node.makeFlattenedList(node.get(_name), params);
			return node.setMethod(Hint.StaticInvocation, m, null);
		}
		throw error(node, "unsupported number of parameters: %d", params.size());
	}

	public Type typeMethodApply(TypedTree node) {
		if (isStaticClassRecv(node)) {
			return this.typeStaticMethodApply(node);
		}
		Type recv = type(node.get(_recv));
		String name = node.getText(_name, "");
		TypedTree args = node.get(_param);
		Type[] types = this.typeApplyArguments(args);
		int start = this.bufferMethods.size();
		this.bufferMatcher.init(recv);
		Method m = this.typeSystem.resolveObjectMethod(recv, this.bufferMatcher, name, types, bufferMethods, args);
		if (m != null) {
			this.resolvedMethod(node, Hint.MethodApply, m, bufferMatcher);
		}
		if (typeSystem.isDynamic(recv)) {
			m = Reflector.getInvokeDynamicMethod(node.get(_param).size());
			if (m != null) {
				node.makeFlattenedList(node.get(_recv), node.newStringConst(name), node.get(_param));
				return node.setMethod(Hint.StaticDynamicInvocation, m, null);
			}
		}
		return this.undefinedMethod(node, start, "method %s of %s", name, name(recv));
	}

	private boolean isStaticClassRecv(TypedTree node) {
		if (node.get(_recv).is(_Name)) {
			Type t = this.typeSystem.resolveType(node.get(_recv), null);
			return t != null;
		}
		return false;
	}

	public Type typeStaticMethodApply(TypedTree node) {
		Class<?> c = TypeSystem.toClass(this.typeSystem.resolveType(node.get(_recv), null));
		String name = node.getText(_name, "");
		TypedTree args = node.get(_param);
		Type[] types = this.typeApplyArguments(args);
		int start = this.bufferMethods.size();
		Method m = this.typeSystem.resolveStaticMethod(c, name, types, bufferMethods, args);
		return m != null ? this.resolvedMethod(node, Hint.Apply, m, null) //
				: this.undefinedMethod(node, start, "static method %s of %s", name, name(c));
	}

	private Type typeUnary(TypedTree node, String name) {
		Type left = type(node.get(_expr));
		Type common = typeSystem.PrimitiveType(left);
		if (left != common) {
			left = this.tryPrecast(common, node, _expr);
		}
		Type[] types = new Type[] { left };
		int start = this.bufferMethods.size();
		Method m = this.typeSystem.resolveFunctionMethod(name, types, bufferMethods, node);
		return m != null ? this.resolvedMethod(node, Hint.StaticInvocation, m, null) //
				: this.undefinedMethod(node, start, "operator %s for %s", OperatorNames.name(name), name(left));
	}

	private Type typeBinary(TypedTree node, String name, BinaryTypeUnifier unifier) {
		Type left = type(node.get(_left));
		Type right = type(node.get(_right));
		Type common = unifier.unify(typeSystem.PrimitiveType(left), typeSystem.PrimitiveType(right));
		if (left != common) {
			left = this.tryPrecast(common, node, _left);
		}
		if (right != common) {
			right = this.tryPrecast(common, node, _right);
		}

		Type[] types = new Type[] { left, right };
		int start = this.bufferMethods.size();
		Method m = this.typeSystem.resolveFunctionMethod(name, types, bufferMethods, node);
		return m != null ? this.resolvedMethod(node, Hint.StaticInvocation, m, null) //
				: this.undefinedMethod(node, start, "operator %s %s %s", name(left), OperatorNames.name(name), name(right));
	}

	private Type tryPrecast(Type req, TypedTree node, Symbol label) {
		TypedTree unode = node.get(label);
		TypedTree cnode = this.typeSystem.makeCast(req, unode);
		if (unode == cnode) {
			return node.getType();
		}
		node.set(label, cnode);
		return req;
	}

	public Type typeAnd(TypedTree node) {
		this.enforceType(boolean.class, node, _left);
		this.enforceType(boolean.class, node, _right);
		return boolean.class;
	}

	public Type typeOr(TypedTree node) {
		this.enforceType(boolean.class, node, _left);
		this.enforceType(boolean.class, node, _right);
		return boolean.class;
	}

	public Type typeNot(TypedTree node) {
		this.enforceType(boolean.class, node, _expr);
		return boolean.class;
	}

	public Type typeAdd(TypedTree node) {
		return typeBinary(node, "opAdd", TypeSystem.UnifyAdditive);
	}

	public Type typeSub(TypedTree node) {
		return typeBinary(node, "opSub", TypeSystem.UnifyAdditive);
	}

	public Type typeMul(TypedTree node) {
		return typeBinary(node, "opMul", TypeSystem.UnifyAdditive);
	}

	public Type typeDiv(TypedTree node) {
		return typeBinary(node, "opDiv", TypeSystem.UnifyAdditive);
	}

	public Type typePlus(TypedTree node) {
		return this.typeUnary(node, "opPlus");
	}

	public Type typeMinus(TypedTree node) {
		return this.typeUnary(node, "opMinus");
	}

	public Type typeEquals(TypedTree node) {
		return typeBinary(node, "opEquals", TypeSystem.UnifyEquator);
	}

	public Type typeNotEquals(TypedTree node) {
		return typeBinary(node, "opNotEquals", TypeSystem.UnifyEquator);
	}

	public Type typeLessThan(TypedTree node) {
		return typeBinary(node, "opLessThan", TypeSystem.UnifyComparator);
	}

	public Type typeLessThanEquals(TypedTree node) {
		return typeBinary(node, "opLessThanEquals", TypeSystem.UnifyComparator);
	}

	public Type typeGreaterThan(TypedTree node) {
		return typeBinary(node, "opGreaterThan", TypeSystem.UnifyComparator);
	}

	public Type typeGreaterThanEquals(TypedTree node) {
		return typeBinary(node, "opGreaterThanEquals", TypeSystem.UnifyComparator);
	}

	public Type typeLeftShift(TypedTree node) {
		return typeBinary(node, "opLeftShift", TypeSystem.UnifyBitwise);
	}

	public Type typeRightShift(TypedTree node) {
		return typeBinary(node, "opRightShift", TypeSystem.UnifyBitwise);
	}

	public Type typeLogicalRightShift(TypedTree node) {
		return this.typeSelfAssign(node, _LogicalRightShift);
	}

	public Type typeBitwiseAnd(TypedTree node) {
		return typeBinary(node, "opBitwiseAnd", TypeSystem.UnifyBitwise);
	}

	public Type typeBitwiseOr(TypedTree node) {
		return typeBinary(node, "opBitwiseOr", TypeSystem.UnifyBitwise);
	}

	public Type typeBitwiseXor(TypedTree node) {
		return typeBinary(node, "opBitwiseXor", TypeSystem.UnifyBitwise);
	}

	public Type typeCompl(TypedTree node) {
		return this.typeUnary(node, "opCompl");
	}

	public Type typeNull(TypedTree node) {
		return Object.class;
	}

	public Type typeTrue(TypedTree node) {
		node.setValue(true);
		return boolean.class;
	}

	public Type typeFalse(TypedTree node) {
		node.setValue(false);
		return boolean.class;
	}

	public Type typeShort(TypedTree node) {
		return typeInteger(node);
	}

	public Type typeInteger(TypedTree node) {
		try {
			String n = node.toText().replace("_", "");
			if (n.startsWith("0b") || n.startsWith("0B")) {
				return node.setConst(int.class, Integer.parseInt(n.substring(2), 2));
			} else if (n.startsWith("0x") || n.startsWith("0X")) {
				return node.setConst(int.class, Integer.parseInt(n.substring(2), 16));
			}
			return node.setConst(int.class, Integer.parseInt(n));
		} catch (NumberFormatException e) {
			this.typeSystem.reportWarning(node, e.getMessage());
		}
		return node.setConst(int.class, 0);
	}

	public Type typeLong(TypedTree node) {
		try {
			String n = node.toText();
			return node.setConst(long.class, Long.parseLong(n));
		} catch (NumberFormatException e) {
			this.typeSystem.reportWarning(node, e.getMessage());
		}
		return node.setConst(long.class, 0L);
	}

	public Type typeFloat(TypedTree node) {
		return typeDouble(node);
	}

	public Type typeDouble(TypedTree node) {
		try {
			String n = node.toText();
			return node.setConst(double.class, Double.parseDouble(n));
		} catch (NumberFormatException e) {
			this.typeSystem.reportWarning(node, e.getMessage());
		}
		return node.setConst(double.class, 0.0);
	}

	public Type typeText(TypedTree node) {
		return node.setConst(String.class, node.toText());
	}

	public Type typeString(TypedTree node) {
		String t = node.toText();
		return node.setConst(String.class, StringUtils.unquoteString(t));
	}

	public Type typeCharacter(TypedTree node) {
		String t = StringUtils.unquoteString(node.toText());
		if (t.length() == 1) {
			return node.setConst(char.class, t.charAt(0));
		}
		return node.setConst(String.class, t);
	}

	public Type typeInterpolation(TypedTree node) {
		for (TypedTree sub : node) {
			type(sub);
		}
		return node.setMethod(Hint.StaticInvocation, this.typeSystem.InterpolationMethod, null);
	}

	/* array */

	public Type typeArray(TypedTree node) {
		Type elementType = Object.class;
		if (node.size() > 0) {
			boolean mixed = false;
			elementType = null;
			for (TypedTree sub : node) {
				Type t = type(sub);
				if (t == elementType) {
					continue;
				}
				if (elementType == null) {
					elementType = t;
				} else {
					mixed = true;
					elementType = Object.class;
				}
			}
			if (mixed) {
				for (int i = 0; i < node.size(); i++) {
					TypedTree sub = node.get(i);
					if (sub.getType() != Object.class) {
						node.set(i, this.typeSystem.enforceType(Object.class, sub));
					}
				}
			}
		}
		Type arrayType = typeSystem.newArrayType(elementType);
		return arrayType;
	}

	// Syntax Sugar

	private Type typeSelfAssign(TypedTree node, Symbol optag) {
		TypedTree op = node.newInstance(optag, 0, null);
		op.make(_left, node.get(_left).dup(), _right, node.get(_right));
		node.set(_right, op);
		node.setTag(_Assign);
		return typeAssign(node);
	}

	public Type typeAssignAdd(TypedTree node) {
		return this.typeSelfAssign(node, _Add);
	}

	public Type typeAssignSub(TypedTree node) {
		return this.typeSelfAssign(node, _Sub);
	}

	public Type typeAssignMul(TypedTree node) {
		return this.typeSelfAssign(node, _Mul);
	}

	public Type typeAssignDiv(TypedTree node) {
		return this.typeSelfAssign(node, _Div);
	}

	public Type typeAssignMod(TypedTree node) {
		return this.typeSelfAssign(node, _Mod);
	}

	public Type typeAssignLeftShift(TypedTree node) {
		return this.typeSelfAssign(node, _LeftShift);
	}

	public Type typeAssignRightShift(TypedTree node) {
		return this.typeSelfAssign(node, _RightShift);
	}

	public Type typeAssignLogicalRightShift(TypedTree node) {
		return this.typeSelfAssign(node, _LogicalRightShift);
	}

	public Type typeAssignBitwiseAnd(TypedTree node) {
		return this.typeSelfAssign(node, _BitwiseAnd);
	}

	public Type typeAssignBitwiseXOr(TypedTree node) {
		return this.typeSelfAssign(node, _BitwiseXor);
	}

	public Type typeAssignBitwiseOr(TypedTree node) {
		return this.typeSelfAssign(node, _BitwiseOr);
	}

	private TypeCheckerException error(TypedTree node, String fmt, Object... args) {
		return this.typeSystem.error(node, fmt, args);
	}

}
