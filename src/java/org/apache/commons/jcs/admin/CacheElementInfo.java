package org.apache.commons.jcs.admin;

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

/**
 * Stores info on a cache element for the template
 */
public class CacheElementInfo
{
    /** element key */
    String key = null;

    /** is it eternal */
    boolean eternal = false;

    /** when it was created */
    String createTime = null;

    /** max life */
    long maxLifeSeconds = -1;

    /** when it will expire */
    long expiresInSeconds = -1;

    /**
     * @return a string representation of the key
     */
    public String getKey()
    {
        return this.key;
    }

    /**
     * @return true if the item does not expire
     */
    public boolean isEternal()
    {
        return this.eternal;
    }

    /**
     * @return the time the object was created
     */
    public String getCreateTime()
    {
        return this.createTime;
    }

    /**
     * Ignored if isEternal
     * @return the longest this object can live.
     */
    public long getMaxLifeSeconds()
    {
        return this.maxLifeSeconds;
    }

    /**
     * Ignored if isEternal
     * @return how many seconds until this object expires.
     */
    public long getExpiresInSeconds()
    {
        return this.expiresInSeconds;
    }

    /**
     * @return string info on the item
     */
    @Override
    public String toString()
    {
        StringBuffer buf = new StringBuffer();
        buf.append( "\nCacheElementInfo " );
        buf.append( "\n Key [" + getKey() + "]" );
        buf.append( "\n Eternal [" + isEternal() + "]" );
        buf.append( "\n CreateTime [" + getCreateTime() + "]" );
        buf.append( "\n MaxLifeSeconds [" + getMaxLifeSeconds() + "]" );
        buf.append( "\n ExpiresInSeconds [" + getExpiresInSeconds() + "]" );

        return buf.toString();
    }
}
