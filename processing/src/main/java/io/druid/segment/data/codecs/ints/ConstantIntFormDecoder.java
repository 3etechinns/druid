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
import io.druid.segment.data.codecs.RandomAccessShapeShiftingFormDecoder;

import java.nio.ByteOrder;
import java.util.Arrays;

public class ConstantIntFormDecoder extends RandomAccessShapeShiftingFormDecoder<ShapeShiftingColumnarInts>
{
  public ConstantIntFormDecoder(final byte logValuesPerChunk, final ByteOrder byteOrder)
  {
    super(logValuesPerChunk, byteOrder);
  }

  @Override
  public final void transform(
      ShapeShiftingColumnarInts columnarInts,
      int startOffset,
      int endOffset,
      int numValues
  )
  {

    final int currentConstant = columnarInts.getCurrentReadBuffer().getInt(startOffset);
    Arrays.fill(columnarInts.getDecodedValues(), currentConstant);
  }

  @Override
  public void transformBuffer(
      ShapeShiftingColumnarInts columnarInts,
      int startOffset,
      int endOffset,
      int numValues
  )
  {
    final int currentConstant = columnarInts.getCurrentReadBuffer().getInt(startOffset);
    columnarInts.setCurrentBytesPerValue(0);
    columnarInts.setCurrentConstant(currentConstant);
  }

  @Override
  public void transformUnsafe(
      ShapeShiftingColumnarInts columnarInts,
      int startOffset,
      int endOffset,
      int numValues
  )
  {
    final int currentConstant = columnarInts.getCurrentReadBuffer().getInt(startOffset);
    columnarInts.setCurrentBytesPerValue(0);
    columnarInts.setCurrentConstant(currentConstant);
  }

  @Override
  public byte getHeader()
  {
    return IntCodecs.CONSTANT;
  }

}
