/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 1997-2010 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * https://glassfish.dev.java.net/public/CDDL+GPL_1_1.html
 * or packager/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at packager/legal/LICENSE.txt.
 *
 * GPL Classpath Exception:
 * Oracle designates this particular file as subject to the "Classpath"
 * exception as provided by Oracle in the GPL Version 2 section of the License
 * file that accompanied this code.
 *
 * Modifications:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 *
 * Contributor(s):
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */

package com.sun.istack.tools;

import com.sun.mirror.type.TypeMirror;
import com.sun.mirror.type.ArrayType;
import com.sun.mirror.type.ClassType;
import com.sun.mirror.type.InterfaceType;
import com.sun.mirror.type.TypeVariable;
import com.sun.mirror.type.VoidType;
import com.sun.mirror.type.WildcardType;
import com.sun.mirror.type.PrimitiveType;

/**
 * Visitor that works on APT {@link TypeMirror} and computes a value.
 *
 * <p>
 * This visitor takes a parameter 'P' so that visitor code can be made stateless.
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class APTTypeVisitor<T,P> {
    public final T apply(TypeMirror type, P param) {
        if( type instanceof ArrayType)
            return onArrayType((ArrayType)type,param);
        if( type instanceof PrimitiveType)
            return onPrimitiveType((PrimitiveType)type,param);
        if (type instanceof ClassType )
            return onClassType((ClassType)type,param);
        if (type instanceof InterfaceType )
            return onInterfaceType((InterfaceType)type,param);
        if (type instanceof TypeVariable )
            return onTypeVariable((TypeVariable)type,param);
        if (type instanceof VoidType )
            return onVoidType((VoidType)type,param);
        if(type instanceof WildcardType)
            return onWildcard((WildcardType) type,param);
        assert false;
        throw new IllegalArgumentException();
    }

    protected abstract T onPrimitiveType(PrimitiveType type, P param);
    protected abstract T onArrayType(ArrayType type, P param);
    protected abstract T onClassType(ClassType type, P param);
    protected abstract T onInterfaceType(InterfaceType type, P param);
    protected abstract T onTypeVariable(TypeVariable type, P param);
    protected abstract T onVoidType(VoidType type, P param);
    protected abstract T onWildcard(WildcardType type, P param);

}
