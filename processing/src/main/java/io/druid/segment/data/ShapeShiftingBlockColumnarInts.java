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

package io.druid.segment.data;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public final class ShapeShiftingBlockColumnarInts extends ShapeShiftingColumnarInts
{
  private int[] decodedValues;

  public ShapeShiftingBlockColumnarInts(
      ByteBuffer buffer,
      int numChunks,
      int numValues,
      byte logValuesPerChunk,
      int offsetsSize,
      ByteOrder byteOrder
  )
  {
    super(buffer, numChunks, numValues, logValuesPerChunk, offsetsSize, byteOrder);

  }

  @Override
  public int get(final int index)
  {
    final int desiredChunk = index >> logValuesPerChunk;

    if (desiredChunk != currentChunk) {
      loadChunk(desiredChunk);
    }

    return decodedValues[index & chunkIndexMask];
  }

  @Override
  public void get(int[] vector, int startIndex, int endIndex)
  {
    final int startChunk = startIndex >> logValuesPerChunk;
    final int endChunk = (endIndex - 1) >> logValuesPerChunk;

    if (startChunk != currentChunk) {
      loadChunk(startChunk);
    }
    if (startChunk == endChunk) {
      final int endPos = (endIndex - startIndex);
      for (int outPos = 0, index = startIndex; outPos < endPos; outPos++, index++) {
        vector[outPos] = decodedValues[index & chunkIndexMask];
      }
    } else {
      // find split index
      int splitIndex = startIndex;
      while ((++splitIndex >> logValuesPerChunk) == startChunk) { }

      final int splitPos = (splitIndex - startIndex);
      for (int outPos = 0, index = startIndex; outPos < splitPos; outPos++, index++) {
        vector[outPos] = decodedValues[index & chunkIndexMask];
      }
      loadChunk(endChunk);
      final int endPos = splitPos + (endIndex - splitIndex);
      for (int outPos = splitPos, index = splitIndex; outPos < endPos; outPos++, index++) {
        vector[outPos] = decodedValues[index & chunkIndexMask];
      }
    }
  }

  @Override
  public void get(int[] vector, int[] indices, int numValues)
  {
    int desiredChunk = indices[0] >> logValuesPerChunk;

    if (desiredChunk != currentChunk) {
      loadChunk(desiredChunk);
    }

    for (int i = 0; i < numValues; ) {
      while (i < numValues && (desiredChunk = (indices[i] >> logValuesPerChunk)) == currentChunk) {
        vector[i] = decodedValues[indices[i] & chunkIndexMask];
        i++;
      }
      loadChunk(desiredChunk);
    }
  }

  @Override
  protected void transform(byte chunkCodec, int chunkStartByte, int chunkEndByte, int chunkNumValues)
  {
    decoders.get(chunkCodec)
            .transform(this, chunkStartByte, chunkEndByte, chunkNumValues);
    this.decodedValues = this.getDecodedValues();
  }
}
