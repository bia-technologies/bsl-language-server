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
package com.github._1c_syntax.bsl.languageserver.codelenses;

import com.github._1c_syntax.bsl.languageserver.codelenses.testrunner.TestRunnerAdapter;
import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.context.FileType;
import com.github._1c_syntax.bsl.languageserver.context.symbol.MethodSymbol;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.lsp4j.CodeLens;
import org.eclipse.lsp4j.Command;
import org.springframework.stereotype.Component;

import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class RunTestCodeLensSupplier implements CodeLensSupplier {

  private final TestRunnerAdapter testRunnerAdapter;

  @Override
  public String getId() {
    return "RunTest";
  }

  @Override
  public List<CodeLens> getCodeLenses(DocumentContext documentContext) {

    if (documentContext.getFileType() == FileType.BSL) {
      return Collections.emptyList();
    }

    var testNames = testRunnerAdapter.getTestNames(documentContext);
    var symbolTree = documentContext.getSymbolTree();

    return testNames.stream()
      .map(symbolTree::getMethodSymbol)
      .flatMap(Optional::stream)
      .map(methodSymbol -> toCodeLens(documentContext, methodSymbol))
      .collect(Collectors.toList());
  }

  @Override
  public CodeLens resolve(DocumentContext documentContext, CodeLens unresolved, CodeLensData data) {
    var path = Paths.get(documentContext.getUri());
    var testName = data.getProperties().get("testName");

    var runText = "1testrunner -run " + path + " " + testName + "\n";

    var command = new Command();
    command.setTitle("⏵ Run test");
    command.setCommand(getCommandId());
    command.setArguments(List.of(Map.of("text", runText)));

    unresolved.setCommand(command);

    return unresolved;
  }

  private CodeLens toCodeLens(DocumentContext documentContext, MethodSymbol method) {
    var testName = method.getName();

    Map<String, Object> args = Map.of( "testName", testName);
    var codeLensData = new CodeLensData(documentContext.getUri(), getId(), args);

    var codeLens = new CodeLens(method.getSubNameRange());
    codeLens.setData(codeLensData);

    return codeLens;
  }

  private static String getCommandId() {
    // todo: check if client is VSCode
    // todo: try to find other IDEs commandId
    return "workbench.action.terminal.sendSequence";
  }
}
