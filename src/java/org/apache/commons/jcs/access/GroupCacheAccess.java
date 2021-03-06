package org.apache.commons.jcs.access;

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
import java.util.HashSet;
import java.util.Set;

import org.apache.commons.jcs.access.behavior.IGroupCacheAccess;
import org.apache.commons.jcs.access.exception.CacheException;
import org.apache.commons.jcs.access.exception.InvalidArgumentException;
import org.apache.commons.jcs.engine.CacheElement;
import org.apache.commons.jcs.engine.behavior.ICacheElement;
import org.apache.commons.jcs.engine.behavior.ICompositeCacheAttributes;
import org.apache.commons.jcs.engine.behavior.IElementAttributes;
import org.apache.commons.jcs.engine.control.CompositeCache;
import org.apache.commons.jcs.engine.control.group.GroupAttrName;
import org.apache.commons.jcs.engine.control.group.GroupId;

/**
 * Access for groups.
 */
public class GroupCacheAccess<K extends Serializable, V extends Serializable>
    extends CacheAccess<K, V>
    implements IGroupCacheAccess<K, V>
{
    /** a typesafe copy of the group cache */
    private final CompositeCache<GroupAttrName<K>, V> groupCache;

    /**
     * Constructor for the GroupCacheAccess object
     * <p>
     * @param cacheControl
     */
    @SuppressWarnings("unchecked")
    public GroupCacheAccess( CompositeCache<K, V> cacheControl )
    {
        super( cacheControl );

        // This is a HACK to allow different types of keys in group caches
        this.groupCache = (CompositeCache<GroupAttrName<K>, V>) this.cacheControl;
    }

    /**
     * Gets the groupAccess attribute of the GroupCacheAccess class.
     * <p>
     * @param region
     * @return The groupAccess value
     * @throws CacheException
     */
    public static <K extends Serializable, V extends Serializable> GroupCacheAccess<K, V> getGroupAccess( String region )
        throws CacheException
    {
        CompositeCache<K, V> cache = getCacheManager().getCache( region );
        return new GroupCacheAccess<K, V>( cache );
    }

    /**
     * Gets the groupAccess attribute of the GroupCacheAccess class.
     * <p>
     * @param region
     * @param icca
     * @return The groupAccess value
     * @throws CacheException
     */
    public static <K extends Serializable, V extends Serializable> GroupCacheAccess<K, V> getGroupAccess( String region, ICompositeCacheAttributes icca )
        throws CacheException
    {
        CompositeCache<K, V> cache = getCacheManager().getCache( region, icca );
        return new GroupCacheAccess<K, V>( cache );
    }

    /**
     * Gets an item out of the cache that is in a specified group.
     * <p>
     * @param name
     *            The key name.
     * @param group
     *            The group name.
     * @return The cached value, null if not found.
     */
    public V getFromGroup( K name, String group )
    {
        ICacheElement<GroupAttrName<K>, V> element = this.groupCache.get( getGroupAttrName( group, name ) );
        return ( element != null ) ? element.getVal() : null;
    }

    /**
     * Internal method used for group functionality.
     * <p>
     * @param group
     * @param name
     * @return GroupAttrName
     */
    private GroupAttrName<K> getGroupAttrName( String group, K name )
    {
        GroupId gid = new GroupId( this.cacheControl.getCacheName(), group );
        return new GroupAttrName<K>( gid, name );
    }

    /**
     * Allows the user to put an object into a group within a particular cache
     * region. This method sets the object's attributes to the default for the
     * region.
     * <p>
     * @param name
     *            The key name.
     * @param groupName
     *            The group name.
     * @param value
     *            The object to cache
     * @throws CacheException
     */
    public void putInGroup( K name, String groupName, V value )
        throws CacheException
    {
        putInGroup( name, groupName, value, null );
    }

    /**
     * Allows the user to put an object into a group within a particular cache
     * region. This method allows the object's attributes to be individually
     * specified.
     * <p>
     * @param name
     *            The key name.
     * @param groupName
     *            The group name.
     * @param value
     *            The object to cache
     * @param attr
     *            The objects attributes.
     * @throws CacheException
     */
    public void putInGroup( K name, String groupName, V value, IElementAttributes attr )
        throws CacheException
    {
        if ( name == null )
        {
            throw new InvalidArgumentException( "Key must not be null" );
        }

        if ( value == null )
        {
            throw new InvalidArgumentException( "Value must not be null" );
        }

        // Create the element and update. This may throw an IOException which
        // should be wrapped by cache access.
        try
        {
            GroupAttrName<K> key = getGroupAttrName( groupName, name );
            CacheElement<GroupAttrName<K>, V> ce =
                new CacheElement<GroupAttrName<K>, V>( this.groupCache.getCacheName(), key, value );

            IElementAttributes attributes = (attr == null) ? this.groupCache.getElementAttributes() : attr;
            ce.setElementAttributes( attributes );

            this.groupCache.update( ce );
        }
        catch ( Exception e )
        {
            throw new CacheException( e );
        }

    }

    /**
     * @param name
     * @param group
     */
    public void remove( K name, String group )
    {
        GroupAttrName<K> key = getGroupAttrName( group, name );
        this.groupCache.remove( key );
    }

    /**
     * Gets the set of keys of objects currently in the group.
     * <p>
     * @param group
     * @return A Set of keys.
     */
    public Set<K> getGroupKeys( String group )
    {
        Set<K> groupKeys = new HashSet<K>();

        for (GroupAttrName<K> gan : this.groupCache.getGroupKeys( group ))
        {
            groupKeys.add(gan.attrName);
        }

        return groupKeys;
    }

    /**
     * Gets the set of group names in the cache
     * <p>
     * @return A Set of group names.
     */
    public Set<String> getGroupNames()
    {
        return this.cacheControl.getGroupNames();
    }

    /**
     * Invalidates a group: remove all the group members
     * <p>
     * @param group
     *            The name of the group to invalidate
     */
    public void invalidateGroup( String group )
    {
        this.groupCache.remove(getGroupAttrName(group, null));
    }
}
