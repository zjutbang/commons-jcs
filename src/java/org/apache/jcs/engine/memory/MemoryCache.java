package org.apache.jcs.engine.memory;

/*
 * Copyright 2001-2004 The Apache Software Foundation. Licensed under the Apache License, Version
 * 2.0 (the "License") you may not use this file except in compliance with the License. You may
 * obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless required by
 * applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See
 * the License for the specific language governing permissions and limitations under the License.
 */

import org.apache.jcs.engine.memory.behavior.IMemoryCache;

/**
 * For the framework. Insures methods a MemoryCache needs to access. Not sure why we use this.
 * Should use the IMemeoryCache interface. I'll change it later.
 * <p>
 * This extends IMemoryCache. There was an aborted attemopt to change the interface naming
 * convention to not use the "I" prefix. At this point, there are too many "I" interfaces to get rid
 * of.
 * <p>
 */
public interface MemoryCache
    extends IMemoryCache
{
    // temporary, for backward compatibility.
}
