/*
 * Licensed to Metamarkets Group Inc. (Metamarkets) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. Metamarkets licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package io.druid.segment.data.codecs.ints;

import io.druid.segment.data.ShapeShiftingColumnarInts;
import io.druid.segment.data.codecs.BaseFormDecoder;
import io.druid.segment.data.codecs.DirectFormDecoder;
import sun.nio.ch.DirectBuffer;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * "Unencoded" decoder, reads full sized integer values from shapeshifting int column read buffer.
 *
 * layout:
 * | header: IntCodecs.UNENCODED (byte) | values  (numValues * Integer.BYTES) |
 */
public final class UnencodedIntFormDecoder extends BaseFormDecoder<ShapeShiftingColumnarInts>
    implements DirectFormDecoder<ShapeShiftingColumnarInts>
{
  public UnencodedIntFormDecoder(byte logValuesPerChunk, ByteOrder byteOrder)
  {
    super(logValuesPerChunk, byteOrder);
  }

  @Override
  public void transform(
      ShapeShiftingColumnarInts columnarInts,
      int startOffset,
      int endOffset,
      int numValues
  )
  {
    final ByteBuffer buffer = columnarInts.getCurrentReadBuffer();
    final int[] decodedChunk = columnarInts.getDecodedValues();
    // todo: call out to bytepack decoder?
    for (int i = 0, pos = startOffset; i < numValues; i++, pos += Integer.BYTES) {
      decodedChunk[i] = buffer.getInt(pos);
    }
  }

  @Override
  public void transformBuffer(
      ShapeShiftingColumnarInts columnarInts,
      int startOffset,
      int endOffset,
      int numValues
  )
  {
    columnarInts.setCurrentBytesPerValue(Integer.BYTES);
    columnarInts.setCurrentBufferOffset(startOffset);
  }

  @Override
  public void transformUnsafe(
      ShapeShiftingColumnarInts columnarInts,
      int startOffset,
      int endOffset,
      int numValues
  )
  {
    final ByteBuffer buffer = columnarInts.getCurrentReadBuffer();
    columnarInts.setCurrentBytesPerValue(Integer.BYTES);
    columnarInts.setCurrentBaseAddress(((DirectBuffer) buffer).address() + startOffset);
  }

  @Override
  public byte getHeader()
  {
    return IntCodecs.UNENCODED;
  }
}
