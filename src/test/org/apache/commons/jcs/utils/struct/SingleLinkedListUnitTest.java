package org.apache.commons.jcs.utils.struct;

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

import org.apache.commons.jcs.utils.struct.SingleLinkedList;

import junit.framework.TestCase;

/**
 * Tests for the simple linked list.
 * <p>
 * @author Aaron Smuts
 */
public class SingleLinkedListUnitTest
    extends TestCase
{
    /**
     * Verify that we get a null and that there are no exceptions.
     */
    public void testTakeFromEmptyList()
    {
        // SETUP
        SingleLinkedList<Object> list = new SingleLinkedList<Object>();

        // DO WORK
        Object result = list.takeFirst();

        // VERIFY
        assertNull( "Shouldn't have anything.", result );
    }

    /**
     * Verify FIFO behavior. Verifies that all items are removed.
     */
    public void testAddABunchAndTakeFromList()
    {
        // SETUP
        SingleLinkedList<Integer> list = new SingleLinkedList<Integer>();

        // DO WORK
        int numToPut = 100;
        for ( int i = 0; i < numToPut; i++ )
        {
            list.addLast( Integer.valueOf( i ) );
        }

        // VERIFY
        assertEquals( "Wrong number in list.", numToPut, list.size() );

        for ( int i = 0; i < numToPut; i++ )
        {
            Integer result = list.takeFirst();
            assertEquals( "Wrong value returned.", Integer.valueOf( i ), result );
        }

        // DO WORK
        Integer result = list.takeFirst();

        // VERIFY
        assertNull( "Shouldn't have anything left.", result );
    }

    /**
     * Verify that after calling clear all items are removed adn the size is 0.
     */
    public void testAddABunchAndClear()
    {
        // SETUP
        SingleLinkedList<Integer> list = new SingleLinkedList<Integer>();

        // DO WORK
        int numToPut = 100;
        for ( int i = 0; i < numToPut; i++ )
        {
            list.addLast( Integer.valueOf( i ) );
        }

        // VERIFY
        assertEquals( "Wrong number in list.", numToPut, list.size() );

        // DO WORK
        list.clear();
        Integer result = list.takeFirst();

        // VERIFY
        assertEquals( "Wrong number in list.", 0, list.size() );
        assertNull( "Shouldn't have anything left.", result );
    }
}
