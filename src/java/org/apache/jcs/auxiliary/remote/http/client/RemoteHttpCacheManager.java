package org.apache.jcs.auxiliary.remote.http.client;

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

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.jcs.auxiliary.AuxiliaryCache;
import org.apache.jcs.auxiliary.AuxiliaryCacheManager;
import org.apache.jcs.auxiliary.remote.RemoteCacheNoWait;
import org.apache.jcs.auxiliary.remote.behavior.IRemoteCacheAttributes;
import org.apache.jcs.auxiliary.remote.behavior.IRemoteCacheClient;
import org.apache.jcs.auxiliary.remote.http.client.behavior.IRemoteHttpCacheClient;
import org.apache.jcs.engine.behavior.ICompositeCacheManager;
import org.apache.jcs.engine.behavior.IElementSerializer;
import org.apache.jcs.engine.behavior.IShutdownObserver;
import org.apache.jcs.engine.control.CompositeCacheManager;
import org.apache.jcs.engine.logging.behavior.ICacheEventLogger;
import org.apache.jcs.utils.config.OptionConverter;

/**
 * This is a very crude copy of the RMI remote manager. It needs a lot of work!
 */
public class RemoteHttpCacheManager
    implements AuxiliaryCacheManager, IShutdownObserver
{
    /** Don't change */
    private static final long serialVersionUID = 798077557166389498L;

    /** The logger */
    private final static Log log = LogFactory.getLog( RemoteHttpCacheManager.class );

    /** Contains mappings of Location instance to RemoteCacheManager instance. */
    private static RemoteHttpCacheManager instance;

    /** Contains instances of RemoteCacheNoWait managed by a RemoteCacheManager instance. */
    static final Map<String, RemoteCacheNoWait<? extends Serializable, ? extends Serializable>> caches =
        new HashMap<String, RemoteCacheNoWait<? extends Serializable, ? extends Serializable>>();

    /** The configuration attributes. */
    private IRemoteCacheAttributes remoteCacheAttributes;

    /** The event logger. */
    private final ICacheEventLogger cacheEventLogger;

    /** The serializer. */
    private final IElementSerializer elementSerializer;

    /** The cache manager listeners will need to use to get a cache. */
    private final ICompositeCacheManager cacheMgr;

    /** Remote cache monitor. */
    private static RemoteHttpCacheMonitor monitor;

    /**
     * Constructs an instance to with the given remote connection parameters. If the connection
     * cannot be made, "zombie" services will be temporarily used until a successful re-connection
     * is made by the monitoring daemon.
     * <p>
     * @param cacheMgr
     * @param cacheEventLogger
     * @param elementSerializer
     */
    private RemoteHttpCacheManager( ICompositeCacheManager cacheMgr, ICacheEventLogger cacheEventLogger,
                                    IElementSerializer elementSerializer )
    {
        this.cacheMgr = cacheMgr;
        this.cacheEventLogger = cacheEventLogger;
        this.elementSerializer = elementSerializer;

        // register shutdown observer
        // TODO add the shutdown observable methods to the interface
        if ( this.cacheMgr instanceof CompositeCacheManager )
        {
            ( (CompositeCacheManager) this.cacheMgr ).registerShutdownObserver( this );
        }
    }

    /**
     * Gets the defaultCattr attribute of the RemoteCacheManager object.
     * <p>
     * @return The defaultCattr value
     */
    public IRemoteCacheAttributes getDefaultCattr()
    {
        return this.remoteCacheAttributes;
    }

    /** @return Returns an instance if it exists. else null. */
    public synchronized static RemoteHttpCacheManager getInstance()
    {
        return instance;
    }

    /**
     * Get the singleton instance.
     * <p>
     * @param cacheMgr
     * @param cacheEventLogger
     * @param elementSerializer
     * @return The instance value
     */
    public synchronized static RemoteHttpCacheManager getInstance( ICompositeCacheManager cacheMgr,
                                                                   ICacheEventLogger cacheEventLogger,
                                                                   IElementSerializer elementSerializer )
    {
        if ( instance == null )
        {
            instance = new RemoteHttpCacheManager( cacheMgr, cacheEventLogger, elementSerializer );
        }

        // Fires up the monitoring daemon.
        if ( monitor == null )
        {
            monitor = RemoteHttpCacheMonitor.getInstance();
            // If the returned monitor is null, it means it's already started
            // elsewhere.
            if ( monitor != null )
            {
                Thread t = new Thread( monitor );
                t.setDaemon( true );
                t.start();
            }
        }

        return instance;
    }

    /**
     * Returns a remote cache for the given cache name.
     * <p>
     * @param cacheName
     * @return The cache value
     */
    public <K extends Serializable, V extends Serializable> AuxiliaryCache<K, V> getCache( String cacheName )
    {
        // TODO get some defaults!
        // Perhaps we will need a manager per URL????
        RemoteHttpCacheAttributes ca = new RemoteHttpCacheAttributes();
        ca.setCacheName( cacheName );
        return getCache( ca );
    }

    /**
     * Gets a RemoteCacheNoWait from the RemoteCacheManager. The RemoteCacheNoWait objects are
     * identified by the cache name value of the RemoteCacheAttributes object.
     * <p>
     * If the client is configured to register a listener, this call results on a listener being
     * created if one isn't already registered with the remote cache for this region.
     * <p>
     * @param cattr
     * @return The cache value
     */
    public <K extends Serializable, V extends Serializable> AuxiliaryCache<K, V> getCache( RemoteHttpCacheAttributes cattr )
    {
        RemoteCacheNoWait<K, V> remoteCacheNoWait = null;

        synchronized ( caches )
        {
            remoteCacheNoWait = (RemoteCacheNoWait<K, V>) caches.get( cattr.getCacheName() + cattr.getUrl() );
            if ( remoteCacheNoWait == null )
            {
                RemoteHttpClientListener<K, V> listener = new RemoteHttpClientListener<K, V>( cattr, cacheMgr );

                IRemoteHttpCacheClient<K, V> remoteService = createRemoteHttpCacheClientForAttributes( cattr );

                IRemoteCacheClient<K, V> remoteCacheClient = new RemoteHttpCache<K, V>( cattr, remoteService, listener );
                remoteCacheClient.setCacheEventLogger( cacheEventLogger );
                remoteCacheClient.setElementSerializer( elementSerializer );

                remoteCacheNoWait = new RemoteCacheNoWait<K, V>( remoteCacheClient );
                remoteCacheNoWait.setCacheEventLogger( cacheEventLogger );
                remoteCacheNoWait.setElementSerializer( elementSerializer );

                caches.put( cattr.getCacheName() + cattr.getUrl(), remoteCacheNoWait );
            }
            // might want to do some listener sanity checking here.
        }

        return remoteCacheNoWait;
    }

    /**
     * This is an extension point. The manager and other classes will only create
     * RemoteHttpCacheClient through this method.
     * <p>
     * @param cattr
     * @return IRemoteHttpCacheClient
     */
    protected <K extends Serializable, V extends Serializable> IRemoteHttpCacheClient<K, V> createRemoteHttpCacheClientForAttributes( RemoteHttpCacheAttributes cattr )
    {
        IRemoteHttpCacheClient<K, V> client = (IRemoteHttpCacheClient<K, V>) OptionConverter.instantiateByClassName( cattr
            .getRemoteHttpClientClassName(), IRemoteHttpCacheClient.class, null );

        if ( client == null )
        {
            if ( log.isInfoEnabled() )
            {
                log.info( "Creating the default client." );
            }
            client = new RemoteHttpCacheClient<K, V>( );
        }
        client.initialize( cattr );
        return client;
    }

    /**
     * Gets the stats attribute of the RemoteCacheManager object
     * <p>
     * @return The stats value
     */
    public String getStats()
    {
        StringBuffer stats = new StringBuffer();
        for (RemoteCacheNoWait<?, ?> c : caches.values())
        {
            if ( c != null )
            {
                stats.append( c.getCacheName() );
            }
        }
        return stats.toString();
    }

    /**
     * Gets the cacheType attribute of the RemoteCacheManager object
     * @return The cacheType value
     */
    public int getCacheType()
    {
        return REMOTE_CACHE;
    }

    /**
     * Shutdown callback from composite cache manager.
     * <p>
     * @see org.apache.jcs.engine.behavior.IShutdownObserver#shutdown()
     */
    public void shutdown()
    {
        if ( log.isInfoEnabled() )
        {
            log.info( "Observed shutdown request." );
        }
        //release();
    }

    /**
     * Logs an event if an event logger is configured.
     * <p>
     * @param source
     * @param eventName
     * @param optionalDetails
     */
    protected void logApplicationEvent( String source, String eventName, String optionalDetails )
    {
        if ( cacheEventLogger != null )
        {
            cacheEventLogger.logApplicationEvent( source, eventName, optionalDetails );
        }
    }
}
