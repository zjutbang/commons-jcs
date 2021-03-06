package org.apache.commons.jcs.auxiliary.disk;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.apache.commons.jcs.auxiliary.AbstractAuxiliaryCacheEventLogging;
import org.apache.commons.jcs.auxiliary.AuxiliaryCache;
import org.apache.commons.jcs.auxiliary.disk.behavior.IDiskCacheAttributes;
import org.apache.commons.jcs.engine.CacheEventQueueFactory;
import org.apache.commons.jcs.engine.CacheInfo;
import org.apache.commons.jcs.engine.CacheStatus;
import org.apache.commons.jcs.engine.behavior.ICache;
import org.apache.commons.jcs.engine.behavior.ICacheElement;
import org.apache.commons.jcs.engine.behavior.ICacheEventQueue;
import org.apache.commons.jcs.engine.behavior.ICacheListener;
import org.apache.commons.jcs.engine.stats.StatElement;
import org.apache.commons.jcs.engine.stats.Stats;
import org.apache.commons.jcs.engine.stats.behavior.IStatElement;
import org.apache.commons.jcs.engine.stats.behavior.IStats;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Abstract class providing a base implementation of a disk cache, which can be easily extended to
 * implement a disk cache for a specific persistence mechanism.
 * <p>
 * When implementing the abstract methods note that while this base class handles most things, it
 * does not acquire or release any locks. Implementations should do so as necessary. This is mainly
 * done to minimize the time spent in critical sections.
 * <p>
 * Error handling in this class needs to be addressed. Currently if an exception is thrown by the
 * persistence mechanism, this class destroys the event queue. Should it also destroy purgatory?
 * Should it dispose itself?
 */
public abstract class AbstractDiskCache<K extends Serializable, V extends Serializable>
    extends AbstractAuxiliaryCacheEventLogging<K, V>
    implements AuxiliaryCache<K, V>
{
    /** Don't change. */
    private static final long serialVersionUID = 6541664080877628324L;

    /** The logger */
    protected static final Log log = LogFactory.getLog( AbstractDiskCache.class );

    /** Generic disk cache attributes */
    private IDiskCacheAttributes diskCacheAttributes = null;

    /**
     * Map where elements are stored between being added to this cache and actually spooled to disk.
     * This allows puts to the disk cache to return quickly, and the more expensive operation of
     * serializing the elements to persistent storage queued for later.
     * <p>
     * If the elements are pulled into the memory cache while the are still in purgatory, writing to
     * disk can be canceled.
     */
    protected Map<K, PurgatoryElement<K, V>> purgatory = new HashMap<K, PurgatoryElement<K, V>>();

    /**
     * The CacheEventQueue where changes will be queued for asynchronous updating of the persistent
     * storage.
     */
    protected ICacheEventQueue<K, V> cacheEventQueue;

    /**
     * Indicates whether the cache is 'alive': initialized, but not yet disposed. Child classes must
     * set this to true.
     */
    protected boolean alive = false;

    /** Every cache will have a name, subclasses must set this when they are initialized. */
    protected String cacheName;

    /** DEBUG: Keeps a count of the number of purgatory hits for debug messages */
    protected int purgHits = 0;

    /**
     * We lock here, so that we cannot get an update after a remove all. an individual removal locks
     * the item.
     */
    protected final ReentrantReadWriteLock removeAllLock = new ReentrantReadWriteLock();

    // ----------------------------------------------------------- constructors

    /**
     * Construct the abstract disk cache, create event queues and purgatory. Child classes should
     * set the alive flag to true after they are initialized.
     * <p>
     * @param attr
     */
    public AbstractDiskCache( IDiskCacheAttributes attr )
    {
        this.diskCacheAttributes = attr;

        this.cacheName = attr.getCacheName();

        // create queue
        CacheEventQueueFactory<K, V> fact = new CacheEventQueueFactory<K, V>();
        this.cacheEventQueue = fact.createCacheEventQueue( new MyCacheListener(), CacheInfo.listenerId, cacheName,
                                                           diskCacheAttributes.getEventQueuePoolName(),
                                                           diskCacheAttributes.getEventQueueType() );

        // create purgatory
        initPurgatory();
    }

    /**
     * Purgatory size of -1 means to use a HashMap with no size limit. Anything greater will use an
     * LRU map of some sort.
     * <p>
     * @TODO Currently setting this to 0 will cause nothing to be put to disk, since it will assume
     *       that if an item is not in purgatory, then it must have been plucked. We should make 0
     *       work, a way to not use purgatory.
     */
    private void initPurgatory()
    {
        // we need this so we can stop the updates from happening after a
        // removeall
        removeAllLock.writeLock().lock();

        try
        {
            synchronized (this)
            {
                if ( diskCacheAttributes.getMaxPurgatorySize() >= 0 )
                {
                    purgatory = new LRUMapJCS<K, PurgatoryElement<K, V>>( diskCacheAttributes.getMaxPurgatorySize() );
                }
                else
                {
                    purgatory = new HashMap<K, PurgatoryElement<K, V>>();
                }
            }
        }
        finally
        {
            removeAllLock.writeLock().unlock();
        }
    }

    // ------------------------------------------------------- interface ICache

    /**
     * Adds the provided element to the cache. Element will be added to purgatory, and then queued
     * for later writing to the serialized storage mechanism.
     * <p>
     * An update results in a put event being created. The put event will call the handlePut method
     * defined here. The handlePut method calls the implemented doPut on the child.
     * <p>
     * @param cacheElement
     * @throws IOException
     * @see org.apache.commons.jcs.engine.behavior.ICache#update
     */
    @Override
    public final void update( ICacheElement<K, V> cacheElement )
        throws IOException
    {
        if ( log.isDebugEnabled() )
        {
            log.debug( "Putting element in purgatory, cacheName: " + cacheName + ", key: " + cacheElement.getKey() );
        }

        try
        {
            // Wrap the CacheElement in a PurgatoryElement
            PurgatoryElement<K, V> pe = new PurgatoryElement<K, V>( cacheElement );

            // Indicates the the element is eligible to be spooled to disk,
            // this will remain true unless the item is pulled back into
            // memory.
            pe.setSpoolable( true );

            // Add the element to purgatory
            synchronized ( purgatory )
            {
                purgatory.put( pe.getKey(), pe );
            }

            // Queue element for serialization
            cacheEventQueue.addPutEvent( pe );
        }
        catch ( IOException ex )
        {
            log.error( "Problem adding put event to queue.", ex );

            cacheEventQueue.destroy();
        }
    }

    /**
     * Check to see if the item is in purgatory. If so, return it. If not, check to see if we have
     * it on disk.
     * <p>
     * @param key
     * @return ICacheElement<K, V> or null
     * @see AuxiliaryCache#get
     */
    @Override
    public final ICacheElement<K, V> get( K key )
    {
        // If not alive, always return null.

        if ( !alive )
        {
            if ( log.isDebugEnabled() )
            {
                log.debug( "get was called, but the disk cache is not alive." );
            }
            return null;
        }

        PurgatoryElement<K, V> pe = null;
        synchronized ( purgatory )
        {
            pe = purgatory.get( key );
        }

        // If the element was found in purgatory
        if ( pe != null )
        {
            purgHits++;

            if ( log.isDebugEnabled() )
            {
                if ( purgHits % 100 == 0 )
                {
                    log.debug( "Purgatory hits = " + purgHits );
                }
            }

            // Since the element will go back to the memory cache, we could set
            // spoolable to false, which will prevent the queue listener from
            // serializing the element. This would not match the disk cache
            // behavior and the behavior of other auxiliaries. Gets never remove
            // items from auxiliaries.
            // Beyond consistency, the items should stay in purgatory and get
            // spooled since the mem cache may be set to 0. If an item is
            // active, it will keep getting put into purgatory and removed. The
            // CompositeCache now does not put an item to memory from disk if
            // the size is 0.
            // Do not set spoolable to false. Just let it go to disk. This
            // will allow the memory size = 0 setting to work well.

            if ( log.isDebugEnabled() )
            {
                log.debug( "Found element in purgatory, cacheName: " + cacheName + ", key: " + key );
            }

            return pe.cacheElement;
        }

        // If we reach this point, element was not found in purgatory, so get
        // it from the cache.
        try
        {
            return doGet( key );
        }
        catch ( Exception e )
        {
            log.error( e );

            cacheEventQueue.destroy();
        }

        return null;
    }

    /**
     * Gets items from the cache matching the given pattern. Items from memory will replace those
     * from remote sources.
     * <p>
     * This only works with string keys. It's too expensive to do a toString on every key.
     * <p>
     * Auxiliaries will do their best to handle simple expressions. For instance, the JDBC disk
     * cache will convert * to % and . to _
     * <p>
     * @param pattern
     * @return a map of K key to ICacheElement<K, V> element, or an empty map if there is no
     *         data matching the pattern.
     * @throws IOException
     */
    @Override
    public Map<K, ICacheElement<K, V>> getMatching( String pattern )
        throws IOException
    {
        // Get the keys from purgatory
        Set<K> keyArray = null;

        // this avoids locking purgatory, but it uses more memory
        synchronized ( purgatory )
        {
            keyArray = new HashSet<K>(purgatory.keySet());
        }

        Set<K> matchingKeys = getKeyMatcher().getMatchingKeysFromArray( pattern, keyArray );

        // call getMultiple with the set
        Map<K, ICacheElement<K, V>> result = processGetMultiple( matchingKeys );

        // Get the keys from disk
        Map<K, ICacheElement<K, V>> diskMatches = doGetMatching( pattern );

        result.putAll( diskMatches );

        return result;
    }

    /**
     * Gets multiple items from the cache based on the given set of keys.
     * <p>
     * @param keys
     * @return a map of K key to ICacheElement<K, V> element, or an empty map if there is no
     *         data in cache for any of these keys
     */
    @Override
    public Map<K, ICacheElement<K, V>> processGetMultiple(Set<K> keys)
    {
        Map<K, ICacheElement<K, V>> elements = new HashMap<K, ICacheElement<K, V>>();

        if ( keys != null && !keys.isEmpty() )
        {
            for (K key : keys)
            {
                ICacheElement<K, V> element = get( key );

                if ( element != null )
                {
                    elements.put( key, element );
                }
            }
        }

        return elements;
    }

    /**
     * The keys in a group.
     * <p>
     * @see org.apache.commons.jcs.auxiliary.AuxiliaryCache#getGroupKeys(java.lang.String)
     */
    public abstract Set<K> getGroupKeys( String groupName );

    /**
     * The group names in the cache.
     * <p>
     * @see org.apache.commons.jcs.auxiliary.AuxiliaryCache#getGroupNames()
     */
    public abstract Set<String> getGroupNames();

    /**
     * Removes are not queued. A call to remove is immediate.
     * <p>
     * @param key
     * @return whether the item was present to be removed.
     * @throws IOException
     * @see org.apache.commons.jcs.engine.behavior.ICache#remove
     */
    @Override
    public final boolean remove( K key )
        throws IOException
    {
        PurgatoryElement<K, V> pe = null;

        synchronized ( purgatory )
        {
            // I'm getting the object, so I can lock on the element
            // Remove element from purgatory if it is there
            pe = purgatory.get( key );
        }

        if ( pe != null )
        {
            synchronized ( pe.getCacheElement() )
            {
                synchronized ( purgatory )
                {
                    purgatory.remove( key );
                }

                // no way to remove from queue, just make sure it doesn't get on
                // disk and then removed right afterwards
                pe.setSpoolable( false );

                // Remove from persistent store immediately
                doRemove( key );
            }
        }
        else
        {
            // Remove from persistent store immediately
            doRemove( key );
        }

        return false;
    }

    /**
     * @throws IOException
     * @see org.apache.commons.jcs.engine.behavior.ICache#removeAll
     */
    @Override
    public final void removeAll()
        throws IOException
    {
        if ( this.diskCacheAttributes.isAllowRemoveAll() )
        {
            // Replace purgatory with a new empty hashtable
            initPurgatory();

            // Remove all from persistent store immediately
            doRemoveAll();
        }
        else
        {
            if ( log.isInfoEnabled() )
            {
                log.info( "RemoveAll was requested but the request was not fulfilled: allowRemoveAll is set to false." );
            }
        }
    }

    /**
     * Adds a dispose request to the disk cache.
     * <p>
     * Disposal proceeds in several steps.
     * <ol>
     * <li>Prior to this call the Composite cache dumped the memory into the disk cache. If it is
     * large then we need to wait for the event queue to finish.
     * <li>Wait until the event queue is empty of until the configured ShutdownSpoolTimeLimit is
     * reached.
     * <li>Call doDispose on the concrete impl.
     * </ol>
     * @throws IOException
     */
    @Override
    public final void dispose()
        throws IOException
    {
        Runnable disR = new Runnable()
        {
            public void run()
            {
                boolean keepGoing = true;
                long total = 0;
                long interval = 100;
                while ( keepGoing )
                {
                    keepGoing = !cacheEventQueue.isEmpty();
                    try
                    {
                        Thread.sleep( interval );
                        total += interval;
                        // log.info( "total = " + total );
                    }
                    catch ( InterruptedException e )
                    {
                        break;
                    }
                }
                log.info( "No longer waiting for event queue to finish: " + cacheEventQueue.getStatistics() );
            }
        };
        Thread t = new Thread( disR );
        t.start();
        // wait up to 60 seconds for dispose and then quit if not done.
        try
        {
            t.join( this.diskCacheAttributes.getShutdownSpoolTimeLimit() * 1000 );
        }
        catch ( InterruptedException ex )
        {
            log.error( "The Shutdown Spool Process was interrupted.", ex );
        }

        log.info( "In dispose, destroying event queue." );
        // This stops the processor thread.
        cacheEventQueue.destroy();

        // Invoke any implementation specific disposal code
        // need to handle the disposal first.
        doDispose();

        alive = false;
    }

    /**
     * @return the region name.
     * @see ICache#getCacheName
     */
    public String getCacheName()
    {
        return cacheName;
    }

    /**
     * Gets basic stats for the abstract disk cache.
     * <p>
     * @return String
     */
    public String getStats()
    {
        return getStatistics().toString();
    }

    /**
     * Returns semi-structured data.
     * <p>
     * @see org.apache.commons.jcs.auxiliary.AuxiliaryCache#getStatistics()
     */
    public IStats getStatistics()
    {
        IStats stats = new Stats();
        stats.setTypeName( "Abstract Disk Cache" );

        ArrayList<IStatElement> elems = new ArrayList<IStatElement>();

        IStatElement se = null;

        se = new StatElement();
        se.setName( "Purgatory Hits" );
        se.setData( "" + purgHits );
        elems.add( se );

        se = new StatElement();
        se.setName( "Purgatory Size" );
        se.setData( "" + purgatory.size() );
        elems.add( se );

        // get the stats from the event queue too
        // get as array, convert to list, add list to our outer list
        IStats eqStats = this.cacheEventQueue.getStatistics();
        IStatElement[] eqSEs = eqStats.getStatElements();
        List<IStatElement> eqL = Arrays.asList( eqSEs );
        elems.addAll( eqL );

        // get an array and put them in the Stats object
        IStatElement[] ses = elems.toArray( new StatElement[0] );
        stats.setStatElements( ses );

        return stats;
    }

    /**
     * @return the status -- alive or disposed from CacheConstants
     * @see ICache#getStatus
     */
    public CacheStatus getStatus()
    {
        return ( alive ? CacheStatus.ALIVE : CacheStatus.DISPOSED );
    }

    /**
     * Size cannot be determined without knowledge of the cache implementation, so subclasses will
     * need to implement this method.
     * <p>
     * @return the number of items.
     * @see ICache#getSize
     */
    public abstract int getSize();

    /**
     * @see org.apache.commons.jcs.engine.behavior.ICacheType#getCacheType
     * @return Always returns DISK_CACHE since subclasses should all be of that type.
     */
    public CacheType getCacheType()
    {
        return CacheType.DISK_CACHE;
    }

    /**
     * Cache that implements the CacheListener interface, and calls appropriate methods in its
     * parent class.
     */
    protected class MyCacheListener
        implements ICacheListener<K, V>
    {
        /** Id of the listener */
        private long listenerId = 0;

        /**
         * @return cacheElement.getElementAttributes();
         * @throws IOException
         * @see ICacheListener#getListenerId
         */
        public long getListenerId()
            throws IOException
        {
            return this.listenerId;
        }

        /**
         * @param id
         * @throws IOException
         * @see ICacheListener#setListenerId
         */
        public void setListenerId( long id )
            throws IOException
        {
            this.listenerId = id;
        }

        /**
         * @param element
         * @throws IOException
         * @see ICacheListener#handlePut NOTE: This checks if the element is a puratory element and
         *      behaves differently depending. However since we have control over how elements are
         *      added to the cache event queue, that may not be needed ( they are always
         *      PurgatoryElements ).
         */
        public void handlePut( ICacheElement<K, V> element )
            throws IOException
        {
            if ( alive )
            {
                // If the element is a PurgatoryElement<K, V> we must check to see
                // if it is still spoolable, and remove it from purgatory.
                if ( element instanceof PurgatoryElement )
                {
                    PurgatoryElement<K, V> pe = (PurgatoryElement<K, V>) element;

                    synchronized ( pe.getCacheElement() )
                    {
                        // TODO consider a timeout.
                        // we need this so that we can have multiple update
                        // threads and still have removeAll requests come in that
                        // always win
                        removeAllLock.readLock().lock();

                        try
                        {
                            // TODO consider changing purgatory sync
                            // String keyAsString = element.getKey().toString();
                            synchronized ( purgatory )
                            {
                                // If the element has already been removed from
                                // purgatory do nothing
                                if ( !purgatory.containsKey( pe.getKey() ) )
                                {
                                    return;
                                }

                                element = pe.getCacheElement();
                            }

                            // I took this out of the purgatory sync block.
                            // If the element is still eligible, spool it.
                            if ( pe.isSpoolable() )
                            {
                                doUpdate( element );
                            }
                        }
                        finally
                        {
                            removeAllLock.readLock().unlock();
                        }

                        synchronized ( purgatory )
                        {
                            // After the update has completed, it is safe to
                            // remove the element from purgatory.
                            purgatory.remove( element.getKey() );
                        }
                    }
                }
                else
                {
                    // call the child's implementation
                    doUpdate( element );
                }
            }
            else
            {
                /*
                 * The cache is not alive, hence the element should be removed from purgatory. All
                 * elements should be removed eventually. Perhaps, the alive check should have been
                 * done before it went in the queue. This block handles the case where the disk
                 * cache fails during normal operations.
                 */
                synchronized ( purgatory )
                {
                    purgatory.remove( element.getKey() );
                }
            }
        }

        /**
         * @param cacheName
         * @param key
         * @throws IOException
         * @see ICacheListener#handleRemove
         */
        public void handleRemove( String cacheName, K key )
            throws IOException
        {
            if ( alive )
            {
                if ( doRemove( key ) )
                {
                    log.debug( "Element removed, key: " + key );
                }
            }
        }

        /**
         * @param cacheName
         * @throws IOException
         * @see ICacheListener#handleRemoveAll
         */
        public void handleRemoveAll( String cacheName )
            throws IOException
        {
            if ( alive )
            {
                doRemoveAll();
            }
        }

        /**
         * @param cacheName
         * @throws IOException
         * @see ICacheListener#handleDispose
         */
        public void handleDispose( String cacheName )
            throws IOException
        {
            if ( alive )
            {
                doDispose();
            }
        }
    }

    /**
     * Before the event logging layer, the subclasses implemented the do* methods. Now the do*
     * methods call the *WithEventLogging method on the super. The *WithEventLogging methods call
     * the abstract process* methods. The children implement the process methods.
     * <p>
     * ex. doGet calls getWithEventLogging, which calls processGet
     */

    /**
     * Get a value from the persistent store.
     * <p>
     * Before the event logging layer, the subclasses implemented the do* methods. Now the do*
     * methods call the *EventLogging method on the super. The *WithEventLogging methods call the
     * abstract process* methods. The children implement the process methods.
     * <p>
     * @param key Key to locate value for.
     * @return An object matching key, or null.
     * @throws IOException
     */
    protected final ICacheElement<K, V> doGet( K key )
        throws IOException
    {
        return super.getWithEventLogging( key );
    }

    /**
     * Get a value from the persistent store.
     * <p>
     * Before the event logging layer, the subclasses implemented the do* methods. Now the do*
     * methods call the *EventLogging method on the super. The *WithEventLogging methods call the
     * abstract process* methods. The children implement the process methods.
     * <p>
     * @param pattern Used to match keys.
     * @return A map of matches..
     * @throws IOException
     */
    protected final Map<K, ICacheElement<K, V>> doGetMatching( String pattern )
        throws IOException
    {
        return super.getMatchingWithEventLogging( pattern );
    }

    /**
     * Add a cache element to the persistent store.
     * <p>
     * Before the event logging layer, the subclasses implemented the do* methods. Now the do*
     * methods call the *EventLogging method on the super. The *WithEventLogging methods call the
     * abstract process* methods. The children implement the process methods.
     * <p>
     * @param cacheElement
     * @throws IOException
     */
    protected final void doUpdate( ICacheElement<K, V> cacheElement )
        throws IOException
    {
        super.updateWithEventLogging( cacheElement );
    }

    /**
     * Remove an object from the persistent store if found.
     * <p>
     * Before the event logging layer, the subclasses implemented the do* methods. Now the do*
     * methods call the *EventLogging method on the super. The *WithEventLogging methods call the
     * abstract process* methods. The children implement the process methods.
     * <p>
     * @param key Key of object to remove.
     * @return whether or no the item was present when removed
     * @throws IOException
     */
    protected final boolean doRemove( K key )
        throws IOException
    {
        return super.removeWithEventLogging( key );
    }

    /**
     * Remove all objects from the persistent store.
     * <p>
     * Before the event logging layer, the subclasses implemented the do* methods. Now the do*
     * methods call the *EventLogging method on the super. The *WithEventLogging methods call the
     * abstract process* methods. The children implement the process methods.
     * <p>
     * @throws IOException
     */
    protected final void doRemoveAll()
        throws IOException
    {
        super.removeAllWithEventLogging();
    }

    /**
     * Dispose of the persistent store. Note that disposal of purgatory and setting alive to false
     * does NOT need to be done by this method.
     * <p>
     * Before the event logging layer, the subclasses implemented the do* methods. Now the do*
     * methods call the *EventLogging method on the super. The *WithEventLogging methods call the
     * abstract process* methods. The children implement the process methods.
     * <p>
     * @throws IOException
     */
    protected final void doDispose()
        throws IOException
    {
        super.disposeWithEventLogging();
    }

    /**
     * Gets the extra info for the event log.
     * <p>
     * @return disk location
     */
    @Override
    public String getEventLoggingExtraInfo()
    {
        return getDiskLocation();
    }

    /**
     * This is used by the event logging.
     * <p>
     * @return the location of the disk, either path or ip.
     */
    protected abstract String getDiskLocation();
}
