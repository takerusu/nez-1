package nez.ast.script.asm;

import java.lang.reflect.Method;
import java.lang.reflect.Type;

import konoha.Function;
import nez.ast.Tree;
import nez.ast.script.CommonSymbols;
import nez.ast.script.GenericType;
import nez.ast.script.GlobalVariable;
import nez.ast.script.Reflector;
import nez.ast.script.TypeSystem;
import nez.ast.script.TypedTree;

public class ScriptCompiler {
	TypeSystem typeSystem;
	final ScriptClassLoader cLoader;
	private ScriptCompilerAsm asm;

	public ScriptCompiler(TypeSystem typeSystem) {
		this.typeSystem = typeSystem;
		this.cLoader = new ScriptClassLoader();
		this.asm = new ScriptCompilerAsm(this.typeSystem, this.cLoader);
		this.typeSystem.init(this);
	}

	public Class<?> compileGlobalVariable(Class<?> type, String name) {
		return this.asm.compileGlobalVariableClass(type, name);
	}

	public Class<?> compileFuncType(String name, Class<?> returnType, Class<?>... paramTypes) {
		return this.asm.compileFuncType(name, returnType, paramTypes);
	}

	public static String nameFuncType(Class<?> returnType, Class<?>... paramTypes) {
		StringBuilder sb = new StringBuilder();
		sb.append("Func");
		sb.append(paramTypes.length);
		sb.append('$');
		sb.append(returnType.getSimpleName());
		for (Class<?> p : paramTypes) {
			sb.append('$');
			sb.append(p.getSimpleName());
		}
		return sb.toString();
	}

	public static String nameFuncType(Type returnType, Type... paramTypes) {
		StringBuilder sb = new StringBuilder();
		sb.append("Func");
		sb.append(paramTypes.length);
		sb.append('$');
		sb.append(name(returnType));
		for (Type p : paramTypes) {
			sb.append('$');
			sb.append(name(p));
		}
		sb.append('$');
		return sb.toString();
	}

	private static String name(Type t) {
		if (t instanceof GenericType) {
			return ((GenericType) t).getRawType().getSimpleName();
		}
		if (t instanceof Class<?>) {
			return ((Class<?>) t).getSimpleName();
		}
		return "Object";
	}

	public Function compileStaticFunctionObject(Method m) {
		Class<?> functype = this.typeSystem.getFuncType(m.getReturnType(), m.getParameterTypes());
		Class<?> c = this.asm.compileFunctionClass(functype, m);
		return (Function) Reflector.newInstance(c);
	}

	public Function compileFunction(Tree<?> node) {
		return null;
	}

	public void compileClassDecl(Tree<?> node) {

	}

	public Class<?> compileFuncDecl(Tree<?> node) {
		String name = node.getText(CommonSymbols._name, null);
		Class<?> function = this.asm.compileStaticFuncDecl(name, (TypedTree) node);
		typeSystem.loadStaticFunctionClass(function, true);
		GlobalVariable gv = typeSystem.getGlobalVariable(name);
		if (gv != null && typeSystem.isFuncType(gv.getType())) {
			Method m = Reflector.findInvokeMethod(function);
			System.out.println("function; " + m);
			if (gv.matchFunction(this.typeSystem, m)) {
				System.out.println("set global function; " + m);
				gv.setFunction(this.compileStaticFunctionObject(m));
			}
		}
		return function;
	}
}
