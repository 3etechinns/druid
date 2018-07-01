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

import io.druid.segment.data.CompressionStrategy;
import io.druid.segment.data.codecs.CompressedFormEncoder;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Int typed {@link CompressedFormEncoder} for compressing a {@link CompressibleIntFormEncoder} with a
 * {@link CompressionStrategy}. The inner encoder encodes to a temporary data buffer, and then data is compressed to
 * the compression buffer.
 *
 * layout:
 * | header: IntCodecs.COMPRESSED (byte) | compressed data (compressedDataBuffer.remaining()) |
 */
public final class CompressedIntFormEncoder extends CompressedFormEncoder<int[], IntFormMetrics>
    implements IntFormEncoder
{
  public CompressedIntFormEncoder(
      byte logValuesPerChunk,
      ByteOrder byteOrder,
      CompressionStrategy strategy,
      CompressibleIntFormEncoder encoder,
      ByteBuffer compressedDataBuffer,
      ByteBuffer uncompressedDataBuffer
  )
  {
    super(logValuesPerChunk, byteOrder, strategy, encoder, compressedDataBuffer, uncompressedDataBuffer);
  }


  @Override
  public byte getHeader()
  {
    return IntCodecs.COMPRESSED;
  }
}
