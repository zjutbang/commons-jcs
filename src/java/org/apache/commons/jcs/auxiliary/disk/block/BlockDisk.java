package org.apache.commons.jcs.auxiliary.disk.block;

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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.Serializable;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.commons.jcs.engine.behavior.IElementSerializer;
import org.apache.commons.jcs.utils.serialization.StandardSerializer;
import org.apache.commons.jcs.utils.struct.SingleLinkedList;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * This class manages reading an writing data to disk. When asked to write a value, it returns a
 * block array. It can read an object from the block numbers in a byte array.
 * <p>
 * @author Aaron Smuts
 */
public class BlockDisk
{
    /** The logger */
    private static final Log log = LogFactory.getLog( BlockDisk.class );

    /** The size of the header that indicates the amount of data stored in an occupied block. */
    public static final byte HEADER_SIZE_BYTES = 4; 
    // N.B. 4 bytes is the size used for ByteBuffer.putInt(int value) and ByteBuffer.getInt()

    /** defaults to 4kb */
    private static final int DEFAULT_BLOCK_SIZE_BYTES = 4 * 1024;

    /** Size of the blocks */
    private int blockSizeBytes;

    /**
     * the total number of blocks that have been used. If there are no free, we will use this to
     * calculate the position of the next block.
     */
    private final AtomicInteger numberOfBlocks = new AtomicInteger(0);

    /** Empty blocks that can be reused. */
    private final SingleLinkedList<Integer> emptyBlocks = new SingleLinkedList<Integer>();

    /** The serializer. */
    protected IElementSerializer elementSerializer;

    /** Location of the spot on disk */
    private final String filepath;

    /** File channel for multiple concurrent reads and writes */
    private final FileChannel fc;

    /** How many bytes have we put to disk */
    private final AtomicLong putBytes = new AtomicLong(0);

    /** How many items have we put to disk */
    private final AtomicLong putCount = new AtomicLong(0);

    /**
     * Constructor for the Disk object
     * <p>
     * @param file
     * @param elementSerializer
     * @exception FileNotFoundException
     */
    public BlockDisk( File file, IElementSerializer elementSerializer )
        throws FileNotFoundException
    {
        this( file, DEFAULT_BLOCK_SIZE_BYTES, elementSerializer );
    }

    /**
     * Creates the file and set the block size in bytes.
     * <p>
     * @param file
     * @param blockSizeBytes
     * @throws FileNotFoundException
     */
    public BlockDisk( File file, int blockSizeBytes )
        throws FileNotFoundException
    {
        this( file, blockSizeBytes, new StandardSerializer() );
    }

    /**
     * Creates the file and set the block size in bytes.
     * <p>
     * @param file
     * @param blockSizeBytes
     * @param elementSerializer
     * @throws FileNotFoundException
     */
    public BlockDisk( File file, int blockSizeBytes, IElementSerializer elementSerializer )
        throws FileNotFoundException
    {
        this.filepath = file.getAbsolutePath();
        RandomAccessFile raf = new RandomAccessFile( filepath, "rw" );
        this.fc = raf.getChannel();

        if ( log.isInfoEnabled() )
        {
            log.info( "Constructing BlockDisk, blockSizeBytes [" + blockSizeBytes + "]" );
        }

        this.blockSizeBytes = blockSizeBytes;
        this.elementSerializer = elementSerializer;
    }

    /**
     * This writes an object to disk and returns the blocks it was stored in.
     * <p>
     * The program flow is as follows:
     * <ol>
     * <li>Serialize the object.</li>
     * <li>Determine the number of blocks needed.</li>
     * <li>Look for free blocks in the emptyBlock list.</li>
     * <li>If there were not enough in the empty list. Take the nextBlock and increment it.</li>
     * <li>If the data will not fit in one block, create sub arrays.</li>
     * <li>Write the subarrays to disk.</li>
     * <li>If the process fails we should decrement the block count if we took from it.</li>
     * </ol>
     * @param object
     * @return the blocks we used.
     * @throws IOException
     */
    protected int[] write( Serializable object )
        throws IOException
    {
        // serialize the object
        byte[] data = elementSerializer.serialize( object );

        if ( log.isDebugEnabled() )
        {
            log.debug( "write, total pre-chunking data.length = " + data.length );
        }

        this.putBytes.addAndGet(data.length);
        this.putCount.incrementAndGet();

        // figure out how many blocks we need.
        int numBlocksNeeded = calculateTheNumberOfBlocksNeeded( data );
        if ( log.isDebugEnabled() )
        {
            log.debug( "numBlocksNeeded = " + numBlocksNeeded );
        }

        int[] blocks = new int[numBlocksNeeded];

        // get them from the empty list or take the next one
        for ( int i = 0; i < numBlocksNeeded; i++ )
        {
            Integer emptyBlock = emptyBlocks.takeFirst();
            if ( emptyBlock != null )
            {
                blocks[i] = emptyBlock.intValue();
            }
            else
            {
                blocks[i] = this.numberOfBlocks.getAndIncrement();
            }
        }

        // get the individual sub arrays.
        byte[][] chunks = getBlockChunks( data, numBlocksNeeded );

        // write the blocks
        for ( int i = 0; i < numBlocksNeeded; i++ )
        {
            int position = calculateByteOffsetForBlock( blocks[i] );
            write( position, chunks[i] );
        }

        return blocks;
    }

    /**
     * Return the amount to put in each block. Fill them all the way, minus the header.
     * <p>
     * @param complete
     * @param numBlocksNeeded
     * @return byte[][]
     */
    protected byte[][] getBlockChunks( byte[] complete, int numBlocksNeeded )
    {
        byte[][] chunks = new byte[numBlocksNeeded][];

        if ( numBlocksNeeded == 1 )
        {
            chunks[0] = complete;
        }
        else
        {
            int maxChunkSize = this.blockSizeBytes - HEADER_SIZE_BYTES;
            int totalBytes = complete.length;
            int totalUsed = 0;
            for ( short i = 0; i < numBlocksNeeded; i++ )
            {
                // use the max that can be written to a block or whatever is left in the original
                // array
                int chunkSize = Math.min( maxChunkSize, totalBytes - totalUsed );
                byte[] chunk = new byte[chunkSize];
                // copy from the used position to the chunk size on the complete array to the chunk
                // array.
                System.arraycopy( complete, totalUsed, chunk, 0, chunkSize );
                chunks[i] = chunk;
                totalUsed += chunkSize;
            }
        }

        return chunks;
    }

    /**
     * Writes the given byte array to the Disk at the specified position.
     * <p>
     * @param position
     * @param data
     * @return true if we wrote successfully
     * @throws IOException
     */
    private boolean write( long position, byte[] data )
        throws IOException
    {
        ByteBuffer buffer = ByteBuffer.allocate(HEADER_SIZE_BYTES + data.length);
        buffer.putInt(data.length);
        buffer.put(data);
        buffer.flip();
        int written = fc.write(buffer, position);
        fc.force(true);

        return written == data.length + HEADER_SIZE_BYTES;
    }

    /**
     * Reads an object that is located in the specified blocks.
     * <p>
     * @param blockNumbers
     * @return Serializable
     * @throws IOException
     * @throws ClassNotFoundException
     */
    protected <T extends Serializable> T read( int[] blockNumbers )
        throws IOException, ClassNotFoundException
    {
        byte[] data = null;

        if ( blockNumbers.length == 1 )
        {
            data = readBlock( blockNumbers[0] );
        }
        else
        {
            ByteArrayOutputStream baos = new ByteArrayOutputStream(getBlockSizeBytes());
            // get all the blocks into data
            for ( short i = 0; i < blockNumbers.length; i++ )
            {
                byte[] chunk = readBlock( blockNumbers[i] );
                baos.write(chunk);
            }

            data = baos.toByteArray();
            baos.close();
        }

        if ( log.isDebugEnabled() )
        {
            log.debug( "read, total post combination data.length = " + data.length );
        }

        return elementSerializer.deSerialize( data );
    }

    /**
     * This reads the occupied data in a block.
     * <p>
     * The first four bytes of the record should tell us how long it is. The data is read into a
     * byte array and then an object is constructed from the byte array.
     * <p>
     * @return byte[]
     * @param block
     * @throws IOException
     */
    private byte[] readBlock( int block )
        throws IOException
    {
        int datalen = 0;

        String message = null;
        boolean corrupted = false;
        long fileLength = fc.size();

        int position = calculateByteOffsetForBlock( block );
//        if ( position > fileLength )
//        {
//            corrupted = true;
//            message = "Record " + position + " starts past EOF.";
//        }
//        else
        {
            ByteBuffer datalength = ByteBuffer.allocate(HEADER_SIZE_BYTES);
            fc.read(datalength, position);
            datalength.flip();
            datalen = datalength.getInt();
            if ( position + datalen > fileLength )
            {
                corrupted = true;
                message = "Record " + position + " exceeds file length.";
            }
        }

        if ( corrupted )
        {
            log.warn( "\n The file is corrupt: " + "\n " + message );
            throw new IOException( "The File Is Corrupt, need to reset" );
        }

        ByteBuffer data = ByteBuffer.allocate(datalen);
        fc.read(data, position + HEADER_SIZE_BYTES);
        data.flip();

        return data.array();
    }

    /**
     * Add these blocks to the emptyBlock list.
     * <p>
     * @param blocksToFree
     */
    protected void freeBlocks( int[] blocksToFree )
    {
        if ( blocksToFree != null )
        {
            for ( short i = 0; i < blocksToFree.length; i++ )
            {
                emptyBlocks.addLast( Integer.valueOf( blocksToFree[i] ) );
            }
        }
    }

    /**
     * Calculates the file offset for a particular block.
     * <p>
     * @param block
     * @return the offset for this block
     */
    protected int calculateByteOffsetForBlock( int block )
    {
        return block * blockSizeBytes;
    }

    /**
     * The number of blocks needed.
     * <p>
     * @param data
     * @return the number of blocks needed to store the byte array
     */
    protected int calculateTheNumberOfBlocksNeeded( byte[] data )
    {
        int dataLength = data.length;

        int oneBlock = blockSizeBytes - HEADER_SIZE_BYTES;

        // takes care of 0 = HEADER_SIZE_BYTES + blockSizeBytes
        if ( dataLength <= oneBlock )
        {
            return 1;
        }

        int dividend = dataLength / oneBlock;

        if ( dataLength % oneBlock != 0 )
        {
            dividend++;
        }
        return dividend;
    }

    /**
     * Returns the file length.
     * <p>
     * @return the size of the file.
     * @exception IOException
     */
    protected long length()
        throws IOException
    {
        return fc.size();
    }

    /**
     * Closes the file.
     * <p>
     * @exception IOException
     */
    protected void close()
        throws IOException
    {
        fc.close();
    }

    /**
     * Resets the file.
     * <p>
     * @exception IOException
     */
    protected synchronized void reset()
        throws IOException
    {
        this.numberOfBlocks.set(0);
        this.emptyBlocks.clear();
        fc.truncate(0);
        fc.force(true);
    }

    /**
     * @return Returns the numberOfBlocks.
     */
    protected int getNumberOfBlocks()
    {
        return numberOfBlocks.get();
    }

    /**
     * @return Returns the blockSizeBytes.
     */
    protected int getBlockSizeBytes()
    {
        return blockSizeBytes;
    }

    /**
     * @return Returns the average size of the an element inserted.
     */
    protected long getAveragePutSizeBytes()
    {
        long count = this.putCount.get();

        if (count == 0 )
        {
            return 0;
        }
        return this.putBytes.get() / count;
    }

    /**
     * @return Returns the number of empty blocks.
     */
    protected int getEmptyBlocks()
    {
        return this.emptyBlocks.size();
    }

    /**
     * For debugging only.
     * <p>
     * @return String with details.
     */
    @Override
    public String toString()
    {
        StringBuffer buf = new StringBuffer();
        buf.append( "\nBlock Disk " );
        buf.append( "\n  Filepath [" + filepath + "]" );
        buf.append( "\n  NumberOfBlocks [" + this.numberOfBlocks.get() + "]" );
        buf.append( "\n  BlockSizeBytes [" + this.blockSizeBytes + "]" );
        buf.append( "\n  Put Bytes [" + this.putBytes + "]" );
        buf.append( "\n  Put Count [" + this.putCount + "]" );
        buf.append( "\n  Average Size [" + getAveragePutSizeBytes() + "]" );
        buf.append( "\n  Empty Blocks [" + this.getEmptyBlocks() + "]" );
        try
        {
            buf.append( "\n  Length [" + length() + "]" );
        }
        catch ( IOException e )
        {
            // swallow
        }
        return buf.toString();
    }

    /**
     * This is used for debugging.
     * <p>
     * @return the file path.
     */
    protected String getFilePath()
    {
        return filepath;
    }
}
