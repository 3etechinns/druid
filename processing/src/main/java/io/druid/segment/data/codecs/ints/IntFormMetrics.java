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

import io.druid.segment.IndexSpec;
import io.druid.segment.data.codecs.FormMetrics;

/**
 * Aggregates statistics about blocks of integer values, such as total number of values processed, minimum and maximum
 * values encountered, if the chunk is constant or all zeros, and various facts about data which is repeated more than
 * twice ('runs') including number of distinct runs, longest run length, and total number of runs. This information is
 * collected by {@link io.druid.segment.data.ShapeShiftingColumnarIntsSerializer} which processing row values, and is
 * provided to {@link IntFormEncoder} implementations to do anything from estimate encoded size to influencing how
 * {@link io.druid.segment.data.ShapeShiftingColumnarIntsSerializer} decides whether or not to employ that particular
 * encoding.
 */
public class IntFormMetrics extends FormMetrics
{
  private int minValue = Integer.MAX_VALUE;
  private int maxValue = Integer.MIN_VALUE;
  private int numRunValues = 0;
  private int numDistinctRuns = 0;
  private int longestRun;
  private int currentRun;
  private int previousValue;
  private int numValues = 0;
  private boolean isFirstValue = true;
  private byte tmpEncodedValuesHolder;

  public IntFormMetrics(IndexSpec.ShapeShiftOptimizationTarget target)
  {
    super(target);
  }

  /**
   * This method is called for every {@link io.druid.segment.data.ShapeShiftingColumnarIntsSerializer#addValue(int)} to
   * aggregate details about a chunk of values.
   *
   * @param val row value
   */
  public void processNextRow(int val)
  {
    if (isFirstValue) {
      isFirstValue = false;
      previousValue = val;
      currentRun = 1;
      longestRun = 1;
    } else {
      if (val == previousValue) {
        currentRun++;
        if (currentRun > 2) {
          numRunValues++;
        }
      } else {
        previousValue = val;
        if (currentRun > 2) {
          numDistinctRuns++;
        }
        currentRun = 1;
      }
    }

    if (currentRun > longestRun) {
      longestRun = currentRun;
    }
    if (val < minValue) {
      minValue = val;
    }
    if (val > maxValue) {
      maxValue = val;
    }
    numValues++;
  }

  @Override
  public int getNumValues()
  {
    return numValues;
  }

  /**
   * Minimum integer value encountered
   *
   * @return
   */
  public int getMinValue()
  {
    return minValue;
  }

  /**
   * Maximum integer value encountered
   *
   * @return
   */
  public int getMaxValue()
  {
    return maxValue;
  }

  /**
   * Total number of values which are part of a 'run', or a repitition of a value 3 or more times
   *
   * @return
   */
  public int getNumRunValues()
  {
    return numRunValues;
  }

  /**
   * Distinct number of 'runs', or values which are repeated more than 2 times
   *
   * @return
   */
  public int getNumDistinctRuns()
  {
    return numDistinctRuns;
  }

  /**
   * Longest number of repeated values
   *
   * @return
   */
  public int getLongestRun()
  {
    return longestRun;
  }

  /**
   * All values are a constant
   *
   * @return
   */
  public boolean isConstant()
  {
    return minValue == maxValue;
  }

  /**
   * All values are zero
   *
   * @return
   */
  public boolean isZero()
  {
    return minValue == 0 && minValue == maxValue;
  }
}
