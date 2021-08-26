/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2021
 * Alexey Sosnoviy <labotamy@gmail.com>, Nikita Gryzlov <nixel2007@gmail.com> and contributors
 *
 * SPDX-License-Identifier: LGPL-3.0-or-later
 *
 * BSL Language Server is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3.0 of the License, or (at your option) any later version.
 *
 * BSL Language Server is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with BSL Language Server.
 */
package com.github._1c_syntax.bsl.languageserver.codelenses.testrunner;

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.utils.CaseInsensitivePattern;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecuteResultHandler;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.ExecuteWatchdog;
import org.apache.commons.exec.PumpStreamHandler;
import org.apache.commons.io.output.ByteArrayOutputStream;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
@Slf4j
public class TestRunnerAdapter {

  private static final Pattern TEST_NAME_PATTERN = CaseInsensitivePattern.compile("^[^<]*<([^>]+)>.*");
  private static final Map<Pair<DocumentContext, Integer>, List<String>> CACHE = new WeakHashMap<>();

  public List<String> getTestNames(DocumentContext documentContext) {
    var cacheKey = Pair.of(documentContext, documentContext.getVersion());

    return CACHE.computeIfAbsent(cacheKey, pair -> computeTestNames(documentContext));
  }

  private List<String> computeTestNames(DocumentContext documentContext) {
    // todo: if win...
    // todo: another test frameworks?
    var path = Paths.get(documentContext.getUri());
    var getTestsCommand = new CommandLine("1testrunner.bat")
      .addArgument("-show")
      .addArgument(path.toString());

    var watchdog = new ExecuteWatchdog(10 * 1000);

    var outputStream = new ByteArrayOutputStream();
    var streamHandler = new PumpStreamHandler(outputStream);

    var resultHandler = new DefaultExecuteResultHandler();

    var executor = new DefaultExecutor();
    executor.setWatchdog(watchdog);
    executor.setStreamHandler(streamHandler);

    try {
      executor.execute(getTestsCommand, resultHandler);
    } catch (IOException e) {
      LOGGER.error("Can't execute 1testrunner -show command", e);
      return Collections.emptyList();
    }
    try {
      resultHandler.waitFor();
    } catch (InterruptedException e) {
      LOGGER.error("Can't wait for 1testrunner -show command", e);
      Thread.currentThread().interrupt();
      return Collections.emptyList();
    }

    // todo: if win... CURSED BE THE DAY
    var charset = Charset.forName("cp866");
    var output = outputStream.toString(charset);

    return Arrays.stream(output.split("\r?\n"))
      .map(TEST_NAME_PATTERN::matcher)
      .filter(Matcher::matches)
      .map(matcher -> matcher.group(1))
      .collect(Collectors.toList());
  }

}
