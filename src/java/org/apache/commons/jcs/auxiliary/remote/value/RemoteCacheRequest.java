package org.apache.commons.jcs.auxiliary.remote.value;

import java.io.Serializable;
import java.util.Set;

import org.apache.commons.jcs.engine.behavior.ICacheElement;

/**
 * The basic request wrapper. The different types of requests are differentiated by their types.
 * <p>
 * Rather than creating sub object types, I created on object that has values for all types of
 * requests.
 */
public class RemoteCacheRequest<K extends Serializable, V extends Serializable>
    implements Serializable
{
    /** Don't change. */
    private static final long serialVersionUID = -8858447417390442569L;

    /** The request type specifies the type of request: get, put, remove, . . */
    private RemoteRequestType requestType = null;

    /** Used to identify the source. Same as listener id on the client side. */
    private long requesterId = 0;

    /** The name of the region */
    private String cacheName;

    /** The key, if this request has a key. */
    private K key;

    /** The keySet, if this request has a keySet. Only getMultiple requests. */
    private Set<K> keySet;

    /** The pattern, if this request uses a pattern. Only getMatching requests. */
    private String pattern;

    /** The ICacheEleemnt, if this request contains a value. Only update requests will have this. */
    private ICacheElement<K, V> cacheElement;

    /**
     * @param requestType the requestType to set
     */
    public void setRequestType( RemoteRequestType requestType )
    {
        this.requestType = requestType;
    }

    /**
     * @return the requestType
     */
    public RemoteRequestType getRequestType()
    {
        return requestType;
    }

    /**
     * @param cacheName the cacheName to set
     */
    public void setCacheName( String cacheName )
    {
        this.cacheName = cacheName;
    }

    /**
     * @return the cacheName
     */
    public String getCacheName()
    {
        return cacheName;
    }

    /**
     * @param key the key to set
     */
    public void setKey( K key )
    {
        this.key = key;
    }

    /**
     * @return the key
     */
    public K getKey()
    {
        return key;
    }

    /**
     * @param pattern the pattern to set
     */
    public void setPattern( String pattern )
    {
        this.pattern = pattern;
    }

    /**
     * @return the pattern
     */
    public String getPattern()
    {
        return pattern;
    }

    /**
     * @param cacheElement the cacheElement to set
     */
    public void setCacheElement( ICacheElement<K, V> cacheElement )
    {
        this.cacheElement = cacheElement;
    }

    /**
     * @return the cacheElement
     */
    public ICacheElement<K, V> getCacheElement()
    {
        return cacheElement;
    }

    /**
     * @param requesterId the requesterId to set
     */
    public void setRequesterId( long requesterId )
    {
        this.requesterId = requesterId;
    }

    /**
     * @return the requesterId
     */
    public long getRequesterId()
    {
        return requesterId;
    }

    /**
     * @param keySet the keySet to set
     */
    public void setKeySet( Set<K> keySet )
    {
        this.keySet = keySet;
    }

    /**
     * @return the keySet
     */
    public Set<K> getKeySet()
    {
        return keySet;
    }

    /** @return string */
    @Override
    public String toString()
    {
        StringBuffer buf = new StringBuffer();
        buf.append( "\nRemoteHttpCacheRequest" );
        buf.append( "\n requesterId [" + getRequesterId() + "]" );
        buf.append( "\n requestType [" + getRequestType() + "]" );
        buf.append( "\n cacheName [" + getCacheName() + "]" );
        buf.append( "\n key [" + getKey() + "]" );
        buf.append( "\n keySet [" + getKeySet() + "]" );
        buf.append( "\n pattern [" + getPattern() + "]" );
        buf.append( "\n cacheElement [" + getCacheElement() + "]" );
        return buf.toString();
    }
}
