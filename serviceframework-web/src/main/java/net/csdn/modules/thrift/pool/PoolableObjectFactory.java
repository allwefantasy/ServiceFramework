/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2012 Oracle and/or its affiliates. All rights reserved.
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

package net.csdn.modules.thrift.pool;

/**
 * An interface defining life-cycle methods for instances to be served by a {@link ObjectPool}
 * <p/>
 * By contract, when an {@link ObjectPool} delegates to a {@link PoolableObjectFactory},
 * {@link #createObject createObject} is called whenever a new instance is needed.
 * {@link #validateObject validateObject} is invoked for making sure
 * they can be {@link ObjectPool#borrowObject borrowed} or {@link ObjectPool#returnObject returned} from the pool.
 * {@link #destroyObject destroyObject} is invoked on every instance when it is being "dropped" from the pool.
 *
 * @author Bongjae Chang
 */
public interface PoolableObjectFactory<K, V> {

    /**
     * Create an instance that can be served by the pool
     *
     * @param key the key used when constructing the object
     * @return an instance that can be served by the pool
     * @throws Exception if there is a problem creating a new instance
     */
    public V createObject(final K key) throws Exception;

    /**
     * Destroy an instance no longer needed by the pool
     *
     * @param key   the key used when selecting the instance
     * @param value the instance to be destroyed
     * @throws Exception if there is a problem destroying {@code value}
     */
    public void destroyObject(final K key, final V value) throws Exception;

    /**
     * Ensures that the instance is safe to be borrowed and returned by the pool
     *
     * @param key   the key used when selecting the object
     * @param value the instance to be validated
     * @return false if {@code value} is not valid and should be dropped from the pool,
     *         true otherwise
     * @throws Exception if there is a problem validating {@code value}.
     *                   an exception should be avoided as it may be swallowed by the pool implementation.
     */
    public boolean validateObject(final K key, final V value) throws Exception;
}
