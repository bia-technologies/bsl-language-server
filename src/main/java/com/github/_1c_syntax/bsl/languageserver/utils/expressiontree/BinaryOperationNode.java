/*
 * This file is a part of BSL Language Server.
 *
 * Copyright (c) 2018-2024
 * Alexey Sosnoviy <labotamy@gmail.com>, Nikita Fedkin <nixel2007@gmail.com> and contributors
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
package com.github._1c_syntax.bsl.languageserver.utils.expressiontree;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import org.antlr.v4.runtime.tree.ParseTree;

@Getter
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class BinaryOperationNode extends BslOperationNode {
  private final BslExpression left;
  private final BslExpression right;

  private BinaryOperationNode(BslOperator operator, BslExpression left, BslExpression right, ParseTree actualSourceCode) {
    super(ExpressionNodeType.BINARY_OP, operator, actualSourceCode);
    this.left = left;
    this.right = right;
  }

  /**
   * Конструирует ветку бинарной операции
   *
   * @param operator         оператор
   * @param left             левая часть операции
   * @param right            правая часть операции
   * @param actualSourceCode строковое представление оператора,
   *                         как он указан в коде с учетом регистра и языка.
   *                         Используется в диагностических сообщениях.
   * @return созданная ветка бинарной операции
   */
  public static BinaryOperationNode create(BslOperator operator, BslExpression left, BslExpression right, ParseTree actualSourceCode) {
    return new BinaryOperationNode(operator, left, right, actualSourceCode);
  }
}
