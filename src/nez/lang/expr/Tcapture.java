package nez.lang.expr;

import nez.ast.SourceLocation;
import nez.lang.Nez;

public class Tcapture extends Nez.New {

	Tcapture(SourceLocation s, int shift) {
		super(shift);
		this.setSourceLocation(s);
	}

}
