package org.apache.jcs.auxiliary.remote;

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
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.jcs.auxiliary.AuxiliaryCacheAttributes;
import org.apache.jcs.auxiliary.remote.behavior.IRemoteCacheClient;
import org.apache.jcs.auxiliary.remote.behavior.IRemoteCacheListener;
import org.apache.jcs.auxiliary.remote.behavior.IRemoteCacheService;
import org.apache.jcs.engine.CacheConstants;
import org.apache.jcs.engine.behavior.ICacheElement;
import org.apache.jcs.engine.behavior.ICacheEventLogger;
import org.apache.jcs.engine.behavior.IElementSerializer;
import org.apache.jcs.engine.stats.behavior.IStats;

/**
 * Used for testing the no wait.
 * <p>
 * @author Aaron Smuts
 */
public class MockRemoteCacheClient
    implements IRemoteCacheClient
{
    /** For serialization. Don't change. */
    private static final long serialVersionUID = 1L;

    /** log instance */
    private final static Log log = LogFactory.getLog( MockRemoteCacheClient.class );

    /** List of ICacheElement objects passed into update. */
    public List updateList = new LinkedList();

    /** List of key objects passed into remove. */
    public List removeList = new LinkedList();

    /** status to return. */
    public int status = CacheConstants.STATUS_ALIVE;

    /** Can setup values to return from get. values must be ICacheElement */
    public Map getSetupMap = new HashMap();

    /** Can setup values to return from get. values must be Map<Serializable, ICacheElement> */
    public Map getMultipleSetupMap = new HashMap();

    /** The last service passed to fixCache */
    public IRemoteCacheService fixed;

    /** Attributes. */
    public RemoteCacheAttributes attributes = new RemoteCacheAttributes();

    /**
     * Stores the last argument as fixed.
     * <p>
     * (non-Javadoc)
     * @see org.apache.jcs.auxiliary.remote.behavior.IRemoteCacheClient#fixCache(org.apache.jcs.auxiliary.remote.behavior.IRemoteCacheService)
     */
    public void fixCache( IRemoteCacheService remote )
    {
        fixed = remote;
    }

    /**
     * @return long
     */
    public long getListenerId()
    {
        return 0;
    }

    /**
     * @return null
     */
    public IRemoteCacheListener getListener()
    {
        return null;
    }

    /**
     * Adds the argument to the updatedList.
     * <p>
     * (non-Javadoc)
     * @see org.apache.jcs.auxiliary.AuxiliaryCache#update(org.apache.jcs.engine.behavior.ICacheElement)
     */
    public void update( ICacheElement ce )
    {
        updateList.add( ce );
    }

    /**
     * Looks in the getSetupMap for a value.
     * <p>
     * (non-Javadoc)
     * @see org.apache.jcs.auxiliary.AuxiliaryCache#get(java.io.Serializable)
     */
    public ICacheElement get( Serializable key )
    {
        log.info( "get [" + key + "]" );
        return (ICacheElement) getSetupMap.get( key );
    }

    /**
     * Gets multiple items from the cache based on the given set of keys.
     * <p>
     * @param keys
     * @return a map of Serializable key to ICacheElement element, or an empty map if there is no
     *         data in cache for any of these keys
     */
    public Map getMultiple( Set keys )
    {
        log.info( "get [" + keys + "]" );
        return (Map) getMultipleSetupMap.get( keys );
    }

    /**
     * Adds the key to the remove list.
     * <p>
     * (non-Javadoc)
     * @see org.apache.jcs.auxiliary.AuxiliaryCache#remove(java.io.Serializable)
     */
    public boolean remove( Serializable key )
    {
        removeList.add( key );
        return false;
    }

    /*
     * (non-Javadoc)
     * @see org.apache.jcs.auxiliary.AuxiliaryCache#removeAll()
     */
    public void removeAll()
    {
        // do nothing
    }

    /*
     * (non-Javadoc)
     * @see org.apache.jcs.auxiliary.AuxiliaryCache#dispose()
     */
    public void dispose()
    {
        // do nothing
    }

    /*
     * (non-Javadoc)
     * @see org.apache.jcs.auxiliary.AuxiliaryCache#getSize()
     */
    public int getSize()
    {
        return 0;
    }

    /**
     * Returns the status setup variable. (non-Javadoc)
     * @see org.apache.jcs.auxiliary.AuxiliaryCache#getStatus()
     */
    public int getStatus()
    {
        return status;
    }

    /*
     * (non-Javadoc)
     * @see org.apache.jcs.auxiliary.AuxiliaryCache#getCacheName()
     */
    public String getCacheName()
    {
        return null;
    }

    /*
     * (non-Javadoc)
     * @see org.apache.jcs.auxiliary.AuxiliaryCache#getGroupKeys(java.lang.String)
     */
    public Set getGroupKeys( String group )
    {
        return null;
    }

    /*
     * (non-Javadoc)
     * @see org.apache.jcs.auxiliary.AuxiliaryCache#getStatistics()
     */
    public IStats getStatistics()
    {
        return null;
    }

    /**
     * Returns the setup attributes. By default they are not null.
     * <p>
     * (non-Javadoc)
     * @see org.apache.jcs.auxiliary.AuxiliaryCache#getAuxiliaryCacheAttributes()
     */
    public AuxiliaryCacheAttributes getAuxiliaryCacheAttributes()
    {
        return attributes;
    }

    /*
     * (non-Javadoc)
     * @see org.apache.jcs.engine.behavior.ICache#getStats()
     */
    public String getStats()
    {
        return null;
    }

    /*
     * (non-Javadoc)
     * @see org.apache.jcs.engine.behavior.ICacheType#getCacheType()
     */
    public int getCacheType()
    {
        return 0;
    }

    public void setCacheEventLogger( ICacheEventLogger cacheEventLogger )
    {
        // TODO Auto-generated method stub
    }

    public void setElementSerializer( IElementSerializer elementSerializer )
    {
        // TODO Auto-generated method stub       
    }
}