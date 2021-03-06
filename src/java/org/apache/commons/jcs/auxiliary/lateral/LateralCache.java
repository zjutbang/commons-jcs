package org.apache.commons.jcs.auxiliary.lateral;

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
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.apache.commons.jcs.auxiliary.AbstractAuxiliaryCacheEventLogging;
import org.apache.commons.jcs.auxiliary.AuxiliaryCacheAttributes;
import org.apache.commons.jcs.auxiliary.lateral.behavior.ILateralCacheAttributes;
import org.apache.commons.jcs.engine.CacheStatus;
import org.apache.commons.jcs.engine.ZombieCacheServiceNonLocal;
import org.apache.commons.jcs.engine.behavior.ICacheElement;
import org.apache.commons.jcs.engine.behavior.ICacheServiceNonLocal;
import org.apache.commons.jcs.engine.behavior.IZombie;
import org.apache.commons.jcs.engine.stats.Stats;
import org.apache.commons.jcs.engine.stats.behavior.IStats;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Lateral distributor. Returns null on get by default. Net search not implemented.
 */
public class LateralCache<K extends Serializable, V extends Serializable>
    extends AbstractAuxiliaryCacheEventLogging<K, V>
{
    /** Don't change. */
    private static final long serialVersionUID = 6274549256562382782L;

    /** The logger. */
    private final static Log log = LogFactory.getLog( LateralCache.class );

    /** generalize this, use another interface */
    private final ILateralCacheAttributes lateralCacheAttribures;

    /** The region name */
    final String cacheName;

    /** either http, socket.udp, or socket.tcp can set in config */
    private ICacheServiceNonLocal<K, V> lateralCacheService;

    /** Monitors the connection. */
    private LateralCacheMonitor monitor;

    /**
     * Constructor for the LateralCache object
     * <p>
     * @param cattr
     * @param lateral
     * @param monitor
     */
    public LateralCache( ILateralCacheAttributes cattr, ICacheServiceNonLocal<K, V> lateral, LateralCacheMonitor monitor )
    {
        this.cacheName = cattr.getCacheName();
        this.lateralCacheAttribures = cattr;
        this.lateralCacheService = lateral;
        this.monitor = monitor;
    }

    /**
     * Constructor for the LateralCache object
     * <p>
     * @param cattr
     */
    public LateralCache( ILateralCacheAttributes cattr )
    {
        this.cacheName = cattr.getCacheName();
        this.lateralCacheAttribures = cattr;
    }

    /**
     * Update lateral.
     * <p>
     * @param ce
     * @throws IOException
     */
    @Override
    protected void processUpdate( ICacheElement<K, V> ce )
        throws IOException
    {
        try
        {
            if ( log.isDebugEnabled() )
            {
                log.debug( "update: lateral = [" + lateralCacheService + "], " + "LateralCacheInfo.listenerId = "
                    + LateralCacheInfo.listenerId );
            }
            lateralCacheService.update( ce, LateralCacheInfo.listenerId );
        }
        catch ( NullPointerException npe )
        {
            log.error( "Failure updating lateral. lateral = " + lateralCacheService, npe );
            handleException( npe, "Failed to put [" + ce.getKey() + "] to " + ce.getCacheName() + "@" + lateralCacheAttribures );
            return;
        }
        catch ( Exception ex )
        {
            handleException( ex, "Failed to put [" + ce.getKey() + "] to " + ce.getCacheName() + "@" + lateralCacheAttribures );
        }
    }

    /**
     * The performance costs are too great. It is not recommended that you enable lateral gets.
     * <p>
     * @param key
     * @return ICacheElement<K, V> or null
     * @throws IOException
     */
    @Override
    protected ICacheElement<K, V> processGet( K key )
        throws IOException
    {
        ICacheElement<K, V> obj = null;

        if ( this.lateralCacheAttribures.getPutOnlyMode() )
        {
            return null;
        }
        try
        {
            obj = lateralCacheService.get( cacheName, key );
        }
        catch ( Exception e )
        {
            log.error( e );
            handleException( e, "Failed to get [" + key + "] from " + lateralCacheAttribures.getCacheName() + "@" + lateralCacheAttribures );
        }
        return obj;
    }

    /**
     * @param pattern
     * @return A map of K key to ICacheElement<K, V> element, or an empty map if there is no
     *         data in cache for any of these keys
     * @throws IOException
     */
    @Override
    protected Map<K, ICacheElement<K, V>> processGetMatching( String pattern )
        throws IOException
    {
        if ( this.lateralCacheAttribures.getPutOnlyMode() )
        {
            return Collections.emptyMap();
        }
        try
        {
            return lateralCacheService.getMatching( cacheName, pattern );
        }
        catch ( IOException e )
        {
            log.error( e );
            handleException( e, "Failed to getMatching [" + pattern + "] from " + lateralCacheAttribures.getCacheName() + "@" + lateralCacheAttribures );
            return Collections.emptyMap();
        }
    }

    /**
     * Gets multiple items from the cache based on the given set of keys.
     * <p>
     * @param keys
     * @return a map of K key to ICacheElement<K, V> element, or an empty map if there is no
     *         data in cache for any of these keys
     * @throws IOException
     */
    @Override
    protected Map<K, ICacheElement<K, V>> processGetMultiple( Set<K> keys )
        throws IOException
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
     * Gets the set of keys of objects currently in the group.
     * <p>
     * @param group
     * @return a Set of group keys.
     * @throws IOException
     */
    public Set<K> getGroupKeys( String groupName )
        throws IOException
    {
        try
        {
            return lateralCacheService.getGroupKeys( cacheName, groupName );
        }
        catch ( Exception ex )
        {
            handleException( ex, "Failed to remove groupName [" + groupName + "] from " + lateralCacheAttribures.getCacheName() + "@"
                + lateralCacheAttribures );
        }
        return Collections.emptySet();
    }

    /**
     * Gets the set of group names in the cache
     * <p>
     * @return a Set of group names.
     * @throws IOException
     */
    public Set<String> getGroupNames()
        throws IOException
    {
        try
        {
            return lateralCacheService.getGroupNames( cacheName );
        }
        catch ( Exception ex )
        {
            handleException( ex, "Failed to get group names from " + lateralCacheAttribures.getCacheName() + "@"
                + lateralCacheAttribures );
        }
        return Collections.emptySet();
    }

    /**
     * Synchronously remove from the remote cache; if failed, replace the remote handle with a
     * zombie.
     * <p>
     * @param key
     * @return false always
     * @throws IOException
     */
    @Override
    protected boolean processRemove( K key )
        throws IOException
    {
        if ( log.isDebugEnabled() )
        {
            log.debug( "removing key:" + key );
        }

        try
        {
            lateralCacheService.remove( cacheName, key, LateralCacheInfo.listenerId );
        }
        catch ( Exception ex )
        {
            handleException( ex, "Failed to remove " + key + " from " + lateralCacheAttribures.getCacheName() + "@" + lateralCacheAttribures );
        }
        return false;
    }

    /**
     * Synchronously removeAll from the remote cache; if failed, replace the remote handle with a
     * zombie.
     * <p>
     * @throws IOException
     */
    @Override
    protected void processRemoveAll()
        throws IOException
    {
        try
        {
            lateralCacheService.removeAll( cacheName, LateralCacheInfo.listenerId );
        }
        catch ( Exception ex )
        {
            handleException( ex, "Failed to remove all from " + lateralCacheAttribures.getCacheName() + "@" + lateralCacheAttribures );
        }
    }

    /**
     * Synchronously dispose the cache. Not sure we want this.
     * <p>
     * @throws IOException
     */
    @Override
    protected void processDispose()
        throws IOException
    {
        log.debug( "Disposing of lateral cache" );

        ///* HELP: This section did nothing but generate compilation warnings.
        // TODO: may limit this functionality. It is dangerous.
        // asmuts -- Added functionality to help with warnings. I'm not getting
        // any.
        try
        {
            lateralCacheService.dispose( this.lateralCacheAttribures.getCacheName() );
            // Should remove connection
        }
        catch ( Exception ex )
        {
            log.error( "Couldn't dispose", ex );
            handleException( ex, "Failed to dispose " + lateralCacheAttribures.getCacheName() );
        }
    }

    /**
     * Returns the cache status.
     * <p>
     * @return The status value
     */
    public CacheStatus getStatus()
    {
        return this.lateralCacheService instanceof IZombie ? CacheStatus.ERROR : CacheStatus.ALIVE;
    }

    /**
     * Returns the current cache size.
     * <p>
     * @return The size value
     */
    public int getSize()
    {
        return 0;
    }

    /**
     * Gets the cacheType attribute of the LateralCache object
     * <p>
     * @return The cacheType value
     */
    public CacheType getCacheType()
    {
        return CacheType.LATERAL_CACHE;
    }

    /**
     * Gets the cacheName attribute of the LateralCache object
     * <p>
     * @return The cacheName value
     */
    public String getCacheName()
    {
        return cacheName;
    }

    /**
     * Not yet sure what to do here.
     * <p>
     * @param ex
     * @param msg
     * @throws IOException
     */
    private void handleException( Exception ex, String msg )
        throws IOException
    {
        log.error( "Disabling lateral cache due to error " + msg, ex );

        lateralCacheService = new ZombieCacheServiceNonLocal<K, V>( lateralCacheAttribures.getZombieQueueMaxSize() );
        // may want to flush if region specifies
        // Notify the cache monitor about the error, and kick off the recovery
        // process.
        monitor.notifyError();

        // could stop the net search if it is built and try to reconnect?
        if ( ex instanceof IOException )
        {
            throw (IOException) ex;
        }
        throw new IOException( ex.getMessage() );
    }

    /**
     * Replaces the current remote cache service handle with the given handle.
     * <p>
     * @param restoredLateral
     */
    public void fixCache( ICacheServiceNonLocal<K, V> restoredLateral )
    {
        if ( this.lateralCacheService != null && this.lateralCacheService instanceof ZombieCacheServiceNonLocal )
        {
            ZombieCacheServiceNonLocal<K, V> zombie = (ZombieCacheServiceNonLocal<K, V>) this.lateralCacheService;
            this.lateralCacheService = restoredLateral;
            try
            {
                zombie.propagateEvents( restoredLateral );
            }
            catch ( Exception e )
            {
                try
                {
                    handleException( e, "Problem propagating events from Zombie Queue to new Lateral Service." );
                }
                catch ( IOException e1 )
                {
                    // swallow, since this is just expected kick back.  Handle always throws
                }
            }
        }
        else
        {
            this.lateralCacheService = restoredLateral;
        }
    }

    /**
     * getStats
     * <p>
     * @return String
     */
    public String getStats()
    {
        return "";
    }

    /**
     * @return Returns the AuxiliaryCacheAttributes.
     */
    public AuxiliaryCacheAttributes getAuxiliaryCacheAttributes()
    {
        return lateralCacheAttribures;
    }

    /**
     * @return debugging data.
     */
    @Override
    public String toString()
    {
        StringBuffer buf = new StringBuffer();
        buf.append( "\n LateralCache " );
        buf.append( "\n Cache Name [" + lateralCacheAttribures.getCacheName() + "]" );
        buf.append( "\n cattr =  [" + lateralCacheAttribures + "]" );
        return buf.toString();
    }

    /**
     * @return extra data.
     */
    @Override
    public String getEventLoggingExtraInfo()
    {
        return null;
    }

    /**
     * The NoWait on top does not call out to here yet.
     * <p>
     * @return almost nothing
     */
    public IStats getStatistics()
    {
        IStats stats = new Stats();
        stats.setTypeName( "LateralCache" );
        return stats;
    }
}
