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
 * Keyed object pooling interface
 *
 * ObjectPool interface mainly defines {@link #borrowObject borrowObject}, {@link #returnObject returnObject} and {@link #removeObject removeObject}.
 *
 * Example of use:
 * {@code
 *     Object obj = null;
 *     try {
 *         obj = pool.borrowObject(key, timeout);
 *         try {
 *         //...use the object...
 *         } catch(Exception e) {
 *             // invalidate the object
 *             try {
 *                 pool.removeObject(key, obj);
 *             } catch(Exception ignore) {
 *             }
 *             // do not return the object to the pool twice
 *             obj = null;
 *         } finally {
 *             // make sure the object is returned to the pool
 *             if(obj != null) {
 *                 try {
 *                     pool.returnObject(key, obj);
 *                 } catch(Exception ignore) {
 *                 }
 *             }
 *         }
 *     } catch(Exception e) {
 *         // failed to borrow an object(pool exhausted, no valid object, interrupted or etc...)
 *     }
 * }
 * @author Bongjae Chang
 */
public interface ObjectPool<K, V> {

    /**
     * Create objects using the {@link org.glassfish.grizzly.thrift.client.pool.PoolableObjectFactory factory} until pool's minimum size, and then place them in the idle object pool
     * <p/>
     * {@code createAllMinObjects} is useful for "pre-loading" a pool with idle objects.
     *
     * @param key the key new instances should be added to
     * @throws Exception if an unexpected exception occurred
     */
    public void createAllMinObjects(final K key) throws Exception;

    /**
     * Obtains an instance from this pool
     * <p/>
     * Instances returned from this method will have been either newly created with
     * {@link org.glassfish.grizzly.thrift.client.pool.PoolableObjectFactory#createObject createObject} or will be a previously idle object and
     * then validated with {@link org.glassfish.grizzly.thrift.client.pool.PoolableObjectFactory#validateObject validateObject}.
     * <p/>
     * By contract, clients should return the borrowed instance using
     * {@link #returnObject returnObject}, {@link #removeObject removeObject}
     * <p/>
     * When the pool has been exhausted, a {@link PoolExhaustedException} will be thrown.
     *
     * @param key             the key used to obtain the object
     * @param timeoutInMillis the max time(milli-second) for borrowing the object. If the pool cannot return an instance in given time,
     *                        {@link PoolExhaustedException} will be thrown.
     * @return an instance from this pool
     * @throws PoolExhaustedException when the pool is exhausted
     * @throws NoValidObjectException when the pool cannot or will not return another instance
     * @throws InterruptedException   when the pool is interrupted
     */
    public V borrowObject(final K key, final long timeoutInMillis) throws PoolExhaustedException, NoValidObjectException, InterruptedException;

    /**
     * Return an instance to the pool
     * <p/>
     * By contract, {@code value} should have been obtained
     * using {@link #borrowObject borrowObject} using a {@code key} that is equivalent to the one used to
     * borrow the instance in the first place.
     *
     * @param key   the key used to obtain the object
     * @param value a {@link #borrowObject borrowed} instance to be returned
     * @throws Exception if an unexpected exception occurred
     */
    public void returnObject(final K key, final V value) throws Exception;

    /**
     * Removes(invalidates) an object from the pool
     * <p/>
     * By contract, {@code value} should have been obtained
     * using {@link #borrowObject borrowObject} using a {@code key} that is equivalent to the one used to
     * borrow the instance in the first place.
     * <p/>
     * This method should be used when an object that has been borrowed
     * is determined (due to an exception or other problem) to be invalid.
     *
     * @param key   the key used to obtain the object
     * @param value a {@link #borrowObject borrowed} instance to be removed
     * @throws Exception if an unexpected exception occurred
     */
    public void removeObject(final K key, final V value) throws Exception;

    /**
     * Clears the specified pool, removing all pooled instances corresponding to the given {@code key}
     *
     * @param key the key to clear
     * @throws Exception if an unexpected exception occurred
     */
    public void removeAllObjects(final K key) throws Exception;

    /**
     * Destroy this pool, and free any resources associated with it
     * <p/>
     * Calling other methods such as {@link #createAllMinObjects createAllMinObjects} or {@link #borrowObject borrowObject},
     * {@link #returnObject returnObject} or {@link #removeObject removeObject} or {@link #removeAllObjects removeAllObjects} after invoking
     * this method on a pool will cause them to throw an {@link IllegalStateException}.
     * </p>
     */
    public void destroy();

    /**
     * Returns the total number of instances
     *
     * @param key the key to query
     * @return the total number of instances corresponding to the given {@code key} currently idle and active in this pool or a negative value if unsupported
     */
    public int getPoolSize(final K key);

    /**
     * Returns the total peak number of instances
     *
     * @param key the key to query
     * @return the peak number of instances corresponding to the given {@code key} or a negative value if unsupported
     */
    public int getPeakCount(final K key);

    /**
     * Returns the number of instances currently borrowed from but not yet returned to the pool
     *
     * @param key the key to query
     * @return the number of instances corresponding to the given {@code key} currently borrowed in this pool or a negative value if unsupported
     */
    public int getActiveCount(final K key);

    /**
     * Returns the number of instances currently idle in this pool
     *
     * @param key the key to query
     * @return the number of instances corresponding to the given {@code key} currently idle in this pool or a negative value if unsupported
     */
    public int getIdleCount(final K key);
}
