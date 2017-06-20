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

package io.druid.parsers.actions;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Preconditions;
import io.druid.data.input.impl.ParseSpec;

import java.util.ArrayList;
import java.util.List;

public class StringParseAction extends ParseAction
{
  private final List<String> input;

  @JsonCreator
  public StringParseAction(
      @JsonProperty("parseSpec") ParseSpec parseSpec,
      @JsonProperty("input") List<String> input
  )
  {
    super(parseSpec);

    Preconditions.checkNotNull(input, "input cannot be null");

    this.input = input;
  }

  @Override
  protected List<ParserInputRow> getInput()
  {
    List<ParserInputRow> rows = new ArrayList<>();
    int counter = 0;
    for (String row : input) {
      rows.add(new ParserInputRow(null, row, ++counter == 1));
    }

    return rows;
  }

  @Override
  public String toString()
  {
    return "StringParseAction{" +
           "input='" + input + '\'' +
           ", parseSpec='" + getParseSpec() + '\'' +
           '}';
  }
}