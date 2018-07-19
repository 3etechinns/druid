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

package io.druid.benchmark;

import com.google.common.collect.ImmutableList;
import io.druid.benchmark.datagen.BenchmarkColumnSchema;
import io.druid.benchmark.datagen.BenchmarkColumnValueGenerator;
import io.druid.segment.column.ValueType;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

@State(Scope.Benchmark)
public class BaseColumnarIntsFromGeneratorBenchmark extends BaseColumnarIntsBenchmark
{
  static BenchmarkColumnValueGenerator makeGenerator(
      String distribution,
      int bits,
      int bound,
      int cardinality,
      int rows
  )
  {
    switch (distribution) {
      case "enumerated":
        ImmutableList<Object> enumerated;
        ImmutableList<Double> probability;

        switch (bits) {
          case 1:
            enumerated = ImmutableList.of(0, 1);
            probability = ImmutableList.of(0.95, 0.001);
            break;
          case 2:
            enumerated = ImmutableList.of(0, 1, 2, 3);
            probability = ImmutableList.of(0.95, 0.001, 0.0189, 0.03);
            break;
          default:
            enumerated = ImmutableList.of(0, 1, bound / 4, bound / 2, 3 * bound / 4, bound);
            probability = ImmutableList.of(0.90, 0.001, 0.0189, 0.03, 0.0001, 0.025);
            break;
        }
        BenchmarkColumnSchema enumeratedSchema = BenchmarkColumnSchema.makeEnumerated(
            "",
            ValueType.INT,
            true,
            1,
            0d,
            enumerated,
            probability
        );
        return enumeratedSchema.makeGenerator(1);
      case "zipfLow":
        BenchmarkColumnSchema zipfLowSchema = BenchmarkColumnSchema.makeZipf(
            "",
            ValueType.INT,
            true,
            1,
            0d,
            Math.max(bound - cardinality, 0),
            bound,
            1d
        );
        return zipfLowSchema.makeGenerator(1);
      case "lazyZipfLow":
        BenchmarkColumnSchema lzipfLowSchema = BenchmarkColumnSchema.makeLazyZipf(
            "",
            ValueType.INT,
            true,
            1,
            0d,
            Math.max(bound - cardinality, 0),
            bound,
            1d
        );
        return lzipfLowSchema.makeGenerator(1);
      case "zipfHi":
        BenchmarkColumnSchema zipfHighSchema = BenchmarkColumnSchema.makeZipf(
            "",
            ValueType.INT,
            true,
            1,
            0d,
            Math.max(bound - cardinality, 0),
            bound,
            3d
        );
        return zipfHighSchema.makeGenerator(1);
      case "lazyZipfHi":
        BenchmarkColumnSchema lzipfHighSchema = BenchmarkColumnSchema.makeLazyZipf(
            "",
            ValueType.INT,
            true,
            1,
            0d,
            Math.max(bound - cardinality, 0),
            bound,
            3d
        );
        return lzipfHighSchema.makeGenerator(1);
      case "nullp50zipfLow":
        BenchmarkColumnSchema nullp50ZipfLow = BenchmarkColumnSchema.makeLazyZipf(
            "",
            ValueType.INT,
            true,
            1,
            0.5,
            Math.max(bound - cardinality, 0),
            bound,
            3d
        );
        return nullp50ZipfLow.makeGenerator(1);
      case "nullp75zipfLow":
        BenchmarkColumnSchema nullp75ZipfLow = BenchmarkColumnSchema.makeLazyZipf(
            "",
            ValueType.INT,
            true,
            1,
            0.75,
            Math.max(bound - cardinality, 0),
            bound,
            1d
        );
        return nullp75ZipfLow.makeGenerator(1);
      case "nullp90zipfLow":
        BenchmarkColumnSchema nullp90ZipfLow = BenchmarkColumnSchema.makeLazyZipf(
            "",
            ValueType.INT,
            true,
            1,
            0.9,
            Math.max(bound - cardinality, 0),
            bound,
            1d
        );
        return nullp90ZipfLow.makeGenerator(1);
      case "nullp95zipfLow":
        BenchmarkColumnSchema nullp95ZipfLow = BenchmarkColumnSchema.makeLazyZipf(
            "",
            ValueType.INT,
            true,
            1,
            0.95,
            Math.max(bound - cardinality, 0),
            bound,
            1d
        );
        return nullp95ZipfLow.makeGenerator(1);
      case "nullp99zipfLow":
        BenchmarkColumnSchema nullp99ZipfLow = BenchmarkColumnSchema.makeLazyZipf(
            "",
            ValueType.INT,
            true,
            1,
            0.99,
            Math.max(bound - cardinality, 0),
            bound,
            1d
        );
        return nullp99ZipfLow.makeGenerator(1);
      case "nullp50zipfHi":
        BenchmarkColumnSchema nullp50ZipfHi = BenchmarkColumnSchema.makeLazyZipf(
            "",
            ValueType.INT,
            true,
            1,
            0.5,
            Math.max(bound - cardinality, 0),
            bound,
            3d
        );
        return nullp50ZipfHi.makeGenerator(1);
      case "nullp75zipfHi":
        BenchmarkColumnSchema nullp75ZipfHi = BenchmarkColumnSchema.makeLazyZipf(
            "",
            ValueType.INT,
            true,
            1,
            0.75,
            Math.max(bound - cardinality, 0),
            bound,
            3d
        );
        return nullp75ZipfHi.makeGenerator(1);
      case "nullp90zipfHi":
        BenchmarkColumnSchema nullp90ZipfHi = BenchmarkColumnSchema.makeLazyZipf(
            "",
            ValueType.INT,
            true,
            1,
            0.9,
            Math.max(bound - cardinality, 0),
            bound,
            3d
        );
        return nullp90ZipfHi.makeGenerator(1);
      case "nullp95zipfHi":
        BenchmarkColumnSchema nullp95ZipfHi = BenchmarkColumnSchema.makeLazyZipf(
            "",
            ValueType.INT,
            true,
            1,
            0.95,
            Math.max(bound - cardinality, 0),
            bound,
            3d
        );
        return nullp95ZipfHi.makeGenerator(1);
      case "nullp99zipfHi":
        BenchmarkColumnSchema nullp99ZipfHi = BenchmarkColumnSchema.makeLazyZipf(
            "",
            ValueType.INT,
            true,
            1,
            0.99,
            Math.max(bound - cardinality, 0),
            bound,
            3d
        );
        return nullp99ZipfHi.makeGenerator(1);
      case "sequential":
        BenchmarkColumnSchema sequentialSchema = BenchmarkColumnSchema.makeSequential(
            "",
            ValueType.INT,
            true,
            1,
            0d,
            Math.max(bound - rows, 0),
            bound
        );
        return sequentialSchema.makeGenerator(1);
      case "sequential-skip":
        BenchmarkColumnSchema sequentialSkipSchema = BenchmarkColumnSchema.makeSequential(
            "",
            ValueType.INT,
            true,
            1,
            0d,
            0,
            bound
        );
        return sequentialSkipSchema.makeGenerator(1);
      case "uniform":
        BenchmarkColumnSchema uniformSchema = BenchmarkColumnSchema.makeDiscreteUniform(
            "",
            ValueType.INT,
            true,
            1,
            0d,
            Math.max(bound - cardinality, 0),
            bound
        );
        return uniformSchema.makeGenerator(1);
      case "lazyUniform":
        BenchmarkColumnSchema lazyUniformSchema = BenchmarkColumnSchema.makeLazyDiscreteUniform(
            "",
            ValueType.INT,
            true,
            1,
            0d,
            Math.max(bound - cardinality, 0),
            bound
        );
        return lazyUniformSchema.makeGenerator(1);
    }
    throw new IllegalArgumentException("unknown distribution");
  }

  //@Param({"3", "9", "18", "27"})
  @Param({"27"})
  int bits;

  @Param({"3000000"})
  int rows;

  //@Param({"lazyZipfLow", "lazyZipfHi", "nullp50zipfLow", "nullp75zipfLow", "nullp90zipfLow", "nullp95zipfLow", "nullp99zipfLow", "lazyUniform", "random"})
  @Param({"nullp95zipfHi"})
  String distribution;

  @Param("150000000")
  int cardinality;

  int bound;

  void initializeValues() throws IOException
  {
    final String filename = getGeneratorValueFilename(distribution, cardinality, bits, rows);
    File dir = getTmpDir();
    File dataFile = new File(dir, filename);

    vals = new int[rows];
    bound = 1 << bits;

    if (dataFile.exists()) {
      System.out.println("Data files already exist, re-using");
      try (BufferedReader br = Files.newBufferedReader(dataFile.toPath(), StandardCharsets.UTF_8)) {
        int lineNum = 0;
        String line;
        while ((line = br.readLine()) != null) {
          vals[lineNum] = Integer.parseInt(line);
          if (vals[lineNum] < minValue) {
            minValue = vals[lineNum];
          }
          if (vals[lineNum] > maxValue) {
            maxValue = vals[lineNum];
          }
          lineNum++;
        }
      }
    } else {
      try (Writer writer = Files.newBufferedWriter(dataFile.toPath(), StandardCharsets.UTF_8)) {
        int atLeastOneMustBeAsLargeAsBound = rand.nextInt(rows);
        if (distribution.equals("random")) {
          for (int i = 0; i < vals.length; ++i) {
            vals[i] = rand.nextInt(bound);
            if (i == atLeastOneMustBeAsLargeAsBound) {
              vals[i] = bound;
            }
            if (vals[i] < minValue) {
              minValue = vals[i];
            }
            if (vals[i] > maxValue) {
              maxValue = vals[i];
            }
            writer.write(vals[i] + "\n");
          }
        } else {
          BenchmarkColumnValueGenerator valueGenerator = makeGenerator(distribution, bits, bound, cardinality, rows);

          for (int i = 0; i < vals.length; ++i) {
            int value;
            Object rowValue = valueGenerator.generateRowValue();
            value = rowValue != null ? (int) rowValue : 0;
            if (i == atLeastOneMustBeAsLargeAsBound) {
              value = bound;
            } else if (distribution.equals("sequential-skip") && bits > 1) {
              int skip = Math.max(bound / cardinality, 1);
              for (int burn = 0; burn < skip; burn++) {
                value = (int) valueGenerator.generateRowValue();
              }
            }
            vals[i] = value;
            if (vals[i] < minValue) {
              minValue = vals[i];
            }
            if (vals[i] > maxValue) {
              maxValue = vals[i];
            }
            writer.write(vals[i] + "\n");
          }
        }
      }
    }
  }

  static String getGeneratorValueFilename(String distribution, int cardinality, int bits, int rows)
  {
    return "values-" + distribution + "-" + cardinality + "-" + bits + "-" + rows + ".bin";
  }

  static String getGeneratorEncodedFilename(String encoding, int bits, String distribution, int rows, int cardinality)
  {
    return encoding + "-" + bits + "-" + distribution + "-" + rows + "-" + cardinality + ".bin";
  }

  static File getTmpDir()
  {
    final String dirPath = "tmp/encoding/ints/";
    File dir = new File(dirPath);
    dir.mkdirs();
    return dir;
  }
}