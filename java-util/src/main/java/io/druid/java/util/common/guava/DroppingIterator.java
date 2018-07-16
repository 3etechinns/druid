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

package io.druid.java.util.common.guava;

import java.util.Iterator;

/**
 */
public class DroppingIterator<T> implements Iterator<T>
{
  private final Iterator<T> delegate;
  private final int numToDrop;
  private boolean dropped = false;

  public DroppingIterator(
      Iterator<T> delegate,
      int numToDrop
  )
  {
    this.delegate = delegate;
    this.numToDrop = numToDrop;
  }

  @Override
  public boolean hasNext()
  {
    if (!dropped) {
      for (int i = 0; i < numToDrop; ++i) {
        delegate.next();
      }
      dropped = true;
    }

    return delegate.hasNext();
  }

  @Override
  public T next()
  {
    if (!dropped) {
      for (int i = 0; i < numToDrop; ++i) {
        delegate.next();
      }
      dropped = true;
    }
    return delegate.next();
  }

  @Override
  public void remove()
  {
    throw new UnsupportedOperationException();
  }
}
