/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 1997-2017 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * http://glassfish.java.net/public/CDDL+GPL_1_1.html
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

package com.sun.istack.localization;

import com.sun.istack.localization.LocalizableMessageFactory.ResourceBundleSupplier;

import java.util.Arrays;
import java.util.Locale;
import java.util.ResourceBundle;


/**
 * @author WS Development Team
 */
public final class LocalizableMessage implements Localizable {

    private final String _bundlename;
    private final ResourceBundleSupplier _rbSupplier;

    private final String _key;
    private final Object[] _args;

    @Deprecated
    public LocalizableMessage(String bundlename, String key, Object... args) {
        this(bundlename, null, key, args);
    }

    public LocalizableMessage(String bundlename, ResourceBundleSupplier rbSupplier,
                              String key, Object... args) {
        _bundlename = bundlename;
        _rbSupplier = rbSupplier;
        _key = key;
        if(args==null)
            args = new Object[0];
        _args = args;
    }

    @Override
    public String getKey() {
        return _key;
    }

    @Override
    public Object[] getArguments() {
        return Arrays.copyOf(_args, _args.length);
    }

    @Override
    public String getResourceBundleName() {
        return _bundlename;
    }

    @Override
    public ResourceBundle getResourceBundle(Locale locale) {
        if (_rbSupplier == null)
            return null;

        return _rbSupplier.getResourceBundle(locale);
    }
}
