/*
 * SoftRefCache.java
 *
 * Created on 17 January 2005, 04:25
 */

package net.sf.yajcache.soft;

import java.lang.ref.ReferenceQueue;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.sf.yajcache.core.CacheEntry;
import net.sf.yajcache.core.ICache;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;


/**
 * Cache implemented using Soft References.
 *
 * @author Hanson Char
 */
public class SoftRefCache<V> implements ICache<V> {
    private static final boolean debug = true;
    private Log log = debug ? LogFactory.getLog(this.getClass()) : null;
    private final ReferenceQueue<V> refq = new ReferenceQueue<V>();
    private final String name;
    private final Class<V> valueType;
    private final Map<String, KeyedSoftRef<V>> map;
    private final KeyedSoftRefCollector<V> collector;
    
    public String getName() {
        return this.name;
    }
    public Class<V> getValueType() {
        return this.valueType;
    }
    public SoftRefCache(String name, Class<V> valueType, int initialCapacity,
                             float loadFactor, int concurrencyLevel) 
    {
        map = new ConcurrentHashMap<String,KeyedSoftRef<V>>(initialCapacity, loadFactor, concurrencyLevel);
        collector = new KeyedSoftRefCollector<V>(refq, map);
        this.name = name;
        this.valueType = valueType;
    }
    public SoftRefCache(String name, Class<V> valueType, int initialCapacity) {
        map = new ConcurrentHashMap<String,KeyedSoftRef<V>>(initialCapacity);
        collector = new KeyedSoftRefCollector<V>(refq, map);
        this.name = name;
        this.valueType = valueType;
    }

    public SoftRefCache(String name, Class<V> valueType) {
        map = new ConcurrentHashMap<String,KeyedSoftRef<V>>();
        collector = new KeyedSoftRefCollector<V>(refq, map);
        this.name = name;
        this.valueType = valueType;
    }

    public boolean isEmpty() {
        this.collector.run();
        return map.isEmpty();
    }

    public int size() {
        this.collector.run();
        return map.size();
    }

    // @tothink: SoftReference.get() doesn't seem to be thread-safe.
    // But do we really want to synchronize upon invoking get() ?
    // It's not thread-safe, but what's the worst consequence ?
    public V get(String key) {
        this.collector.run();
        KeyedSoftRef<V> ref = map.get(key);
        
        if (ref == null)
            return null;
        V val = ref.get();
        
        if (val == null) {
            // already garbage collected.  So try to clean up the key.
            SoftRefCacheCleaner.inst.cleanupKey(this.map, key);
        }
        // cache value exists.
        // try to refresh the soft reference.
//        this.renewSoftReference(key, val);
        return val;
    }
//    private void renewSoftReference(String key, V val) {
//        if (debug)
//            log.debug("get: try to refresh the soft reference.");
//        KeyedSoftRef<V> oldRef = 
//                map.put(key, new KeyedSoftRef<V>(key, val, refq));
//        // Check for race conditon.
//        if (oldRef == null) {
//            // key has just been removed by another thread.
//            if (debug)
//                log.debug("get: key has just been removed by another thread.");
//            return;
//        }
//        V oldVal = oldRef.get();
//        // if oldVal is null, it means the GC just cleared it.
//        while (oldVal != null && oldVal != val) {
//            // race condition occurred
//            // put back the old stuff
//            if (debug)
//                log.debug("get: race condition occurred. put back the old stuff");
//            val = oldVal;
//            oldRef = map.put(key, oldRef);
//            
//            if (oldRef == null) {
//                // key has just been removed by another thread.
//                if (debug)
//                    log.debug("get: key has just been removed by another thread.");
//                oldRef = map.remove(key);
//                
//                if (oldRef == null) {
//                    // again, key has just been removed by another thread.
//                    if (debug)
//                        log.debug("again: key has just been removed by another thread.");
//                    break;
//                }
//            }
//            oldVal = oldRef.get();
//        }
//        return;
//    }
        
    public V get(Object key) {
        return key == null ? null : this.get(key.toString());
    }
    public V put(String key, V value) {
        this.collector.run();
        KeyedSoftRef<V> oldRef = map.put(key, new KeyedSoftRef<V>(key, value, refq));
        
        if (oldRef == null)
            return null;
        V ret = oldRef.get();
        oldRef.clear();
        return ret;
    }
    public void putAll(Map<? extends String, ? extends V> map) {
        for (Map.Entry<? extends String, ? extends V> e : map.entrySet())
            this.put(e.getKey(), e.getValue());
    }
    public V remove(String key) {
        this.collector.run();
        KeyedSoftRef<V> oldRef = map.remove(key);
        
        if (oldRef == null)
            return null;
        V ret = oldRef.get();
        oldRef.clear();
        return ret;
    }
    public V remove(Object key) {
        return key == null ? null : this.remove(key.toString());
    }
    public void clear() {
        this.collector.run();
        map.clear();
    }
    public Set<String> keySet() {
        this.collector.run();
        return map.keySet();
    }
    public Set<Map.Entry<String,V>> entrySet() {
        this.collector.run();
        Set<Map.Entry<String,KeyedSoftRef<V>>> fromSet = map.entrySet();
        Set<Map.Entry<String,V>> toSet = new HashSet<Map.Entry<String,V>>();
        
        for (Map.Entry<String,KeyedSoftRef<V>> item : fromSet) {
            KeyedSoftRef<V> ref = item.getValue();
            V val = ref.get();
            
            if (val != null) {
                Map.Entry<String,V> e = new CacheEntry<V>(item.getKey(), val);
                toSet.add(e);
            }
        }
        return toSet;
    }
    public Collection<V> values() {
        this.collector.run();
        Collection<KeyedSoftRef<V>> fromSet = map.values();
        List<V> toCol = new ArrayList<V>(fromSet.size());
        
        for (KeyedSoftRef<V> ref : fromSet) {
            V val = ref.get();
            
            if (val != null) {
                toCol.add(val);
            }
        }
        return toCol;
    }
    public boolean containsKey(Object key) {
        if (key == null)
            return false;
        return this.get(key.toString()) != null;
    }
    public boolean containsValue(Object value) {
        this.collector.run();
        Collection<KeyedSoftRef<V>> fromSet = map.values();
        
        for (KeyedSoftRef<V> ref : fromSet) {
            V val = ref.get();
            
            if (value.equals(val))
                return true;
        }
        return false;
    }
    /** Returns the number of Soft References collected by GC. */
    public int getCollectorCount() {
        return this.collector.getCount();
    }
}