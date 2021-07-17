/*
 * This file is a part of BSL Language Server.
 *
 * Copyright © 2018-2021
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
package com.github._1c_syntax.bsl.languageserver.providers;

import com.github._1c_syntax.bsl.languageserver.context.DocumentContext;
import com.github._1c_syntax.bsl.languageserver.utils.Ranges;
import com.github._1c_syntax.bsl.languageserver.utils.Trees;
import com.github._1c_syntax.bsl.parser.BSLParser;
import com.github._1c_syntax.utils.CaseInsensitivePattern;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.antlr.v4.runtime.Token;
import org.eclipse.lsp4j.Color;
import org.eclipse.lsp4j.ColorInformation;
import org.eclipse.lsp4j.ColorPresentation;
import org.eclipse.lsp4j.ColorPresentationParams;
import org.eclipse.lsp4j.DocumentColorParams;
import org.eclipse.lsp4j.TextEdit;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
public class ColorProvider {

  private static final Pattern QUOTE_PATTERN = Pattern.compile("\"");
  private static final Pattern COLOR_PATTERN = CaseInsensitivePattern.compile("^(?:Цвет|Color)$");
  private static final Pattern WEB_COLOR_PATTERN = CaseInsensitivePattern.compile("^(?:WebЦвета|WebColors)$");
  private static final Map<String, WebColor> WEB_COLORS = createWebColors();

  private static final double DEFAULT_ALPHA_CHANNEL = 1.0;
  private static final int COLOR_MAX_VALUE = 255;

  public List<ColorInformation> getDocumentColor(DocumentContext documentContext, DocumentColorParams params) {

    var colorInformation = new ArrayList<ColorInformation>();

    var newExpressions = Trees.findAllRuleNodes(
      documentContext.getAst(),
      BSLParser.RULE_newExpression
    );

    newExpressions.stream()
      .map(BSLParser.NewExpressionContext.class::cast)
      .filter(newExpression -> COLOR_PATTERN.matcher(typeName(newExpression)).matches())
      .map(ColorProvider::toColorInformation)
      .collect(Collectors.toCollection(() -> colorInformation));

    var complexIdentifiers = Trees.findAllRuleNodes(
      documentContext.getAst(),
      BSLParser.RULE_complexIdentifier
    );

    complexIdentifiers.stream()
      .map(BSLParser.ComplexIdentifierContext.class::cast)
      .filter(complexIdentifier -> complexIdentifier.IDENTIFIER() != null)
      .filter(complexIdentifier -> complexIdentifier.modifier().size() == 1)
      .filter(complexIdentifier -> complexIdentifier.modifier(0).accessProperty() != null)
      .filter(complexIdentifier -> WEB_COLOR_PATTERN.matcher(complexIdentifier.IDENTIFIER().getText()).matches())
      .map(ColorProvider::toColorInformation)
      .collect(Collectors.toCollection(() -> colorInformation));

    return colorInformation;
  }

  public List<ColorPresentation> getColorPresentation(DocumentContext documentContext, ColorPresentationParams params) {

    var range = params.getRange();
    var color = params.getColor();

    int red = (int) (color.getRed() * COLOR_MAX_VALUE);
    int green = (int) (color.getGreen() * COLOR_MAX_VALUE);
    int blue = (int) (color.getBlue() * COLOR_MAX_VALUE);

    var colorPresentations = new ArrayList<ColorPresentation>();

    colorPresentations.add(
      new ColorPresentation(
        "Через конструктор",
        new TextEdit(range, String.format("Новый Цвет(%d, %d, %d)", red, green, blue))
      )
    );

    WebColor.findByColor(red, green, blue).ifPresent(webColor ->
      colorPresentations.add(
        new ColorPresentation(
          "Через WebЦвет",
          new TextEdit(range, "WebЦвета." + webColor.getRu())
        )
      )
    );

    return colorPresentations;

  }


  private static String typeName(BSLParser.NewExpressionContext ctx) {
    if (ctx.typeName() != null) {
      return ctx.typeName().getText();
    }

    if (ctx.doCall() == null || ctx.doCall().callParamList().isEmpty()) {
      return "";
    }

    return QUOTE_PATTERN.matcher(ctx.doCall().callParamList().callParam(0).getText()).replaceAll("");
  }

  private static ColorInformation toColorInformation(BSLParser.NewExpressionContext ctx) {
    byte redPosition;
    byte greenPosition;
    byte bluePosition;

    if (ctx.typeName() != null) {
      redPosition = 0;
      greenPosition = 1;
      bluePosition = 2;
    } else {
      redPosition = 1;
      greenPosition = 2;
      bluePosition = 3;
    }

    var callParams = Optional.ofNullable(ctx.doCall())
      .map(BSLParser.DoCallContext::callParamList)
      .orElseGet(() -> new BSLParser.CallParamListContext(null, 0));

    double red = getColorValue(callParams, redPosition);
    double green = getColorValue(callParams, greenPosition);
    double blue = getColorValue(callParams, bluePosition);

    var range = Ranges.create(ctx);
    var color = new Color(red, green, blue, DEFAULT_ALPHA_CHANNEL);

    return new ColorInformation(range, color);
  }

  private static Double getColorValue(BSLParser.CallParamListContext callParams, byte colorPosition) {
    return Optional.ofNullable(callParams.callParam(colorPosition))
      .map(BSLParser.CallParamContext::expression)
      .filter(expression -> expression.getTokens().size() == 1)
      .map(expression -> expression.getTokens().get(0))
      .map(Token::getText)
      .map(ColorProvider::tryParseInteger)
      .map(colorValue -> (double) colorValue / COLOR_MAX_VALUE)
      .orElse(0.0);
  }

  private static Integer tryParseInteger(String value) {
    try {
      return Integer.parseInt(value);
    } catch (NumberFormatException e) {
      return 0;
    }
  }

  private static ColorInformation toColorInformation(BSLParser.ComplexIdentifierContext ctx) {
    var colorName = ctx.modifier(0).accessProperty().IDENTIFIER().getText();
    var webColor = WEB_COLORS.get(colorName);

    double red = (double) webColor.getRed() / COLOR_MAX_VALUE;
    double green = (double) webColor.getGreen() / COLOR_MAX_VALUE;
    double blue = (double) webColor.getBlue() / COLOR_MAX_VALUE;

    var range = Ranges.create(ctx);
    var color = new Color(red, green, blue, DEFAULT_ALPHA_CHANNEL);

    return new ColorInformation(range, color);
  }

  private static Map<String, WebColor> createWebColors() {
    var colors = new TreeMap<String, WebColor>(String.CASE_INSENSITIVE_ORDER);
    for (WebColor color : WebColor.values()) {
      colors.put(color.getRu(), color);
      colors.put(color.getEn(), color);
    }

    return colors;
  }


  @AllArgsConstructor
  @Getter
  private enum WebColor {

    AQUAMARINE("Аквамарин", "Aquamarine", 127, 255, 212),
    ALICE_BLUE("АкварельноСиний", "AliceBlue", 240, 248, 255),
    ANTIQUE_WHITE("АнтикБелый", "AntiqueWhite", 250, 235, 215),
    BEIGE("Бежевый", "Beige", 245, 245, 220),
    SNOW("Белоснежный", "Snow", 255, 250, 250),
    WHITE("Белый", "White", 255, 255, 255),
    TURQUOISE("Бирюзовый", "Turquoise", 64, 224, 208),
    PALE_TURQUOISE("БледноБирюзовый", "PaleTurquoise", 175, 238, 238),
    PALE_GREEN("БледноЗеленый", "PaleGreen", 152, 251, 152),
    PALE_GOLDENROD("БледноЗолотистый", "PaleGoldenrod", 238, 232, 170),
    PALE_VIOLET_RED("БледноКрасноФиолетовый", "PaleVioletRed", 219, 112, 147),
    LAVENDER("БледноЛиловый", "Lavender", 230, 230, 250),
    BLANCHED_ALMOND("БледноМиндальный", "BlanchedAlmond", 255, 235, 205),
    THISTLE("БледноСиреневый", "Thistle", 216, 191, 216),
    CORN_FLOWER_BLUE("Васильковый", "CornFlowerBlue", 100, 149, 237),
    SPRING_GREEN("ВесеннеЗеленый", "SpringGreen", 0, 255, 127),
    LIGHT_BLUE("Голубой", "LightBlue", 173, 216, 230),
    LAVENDER_BLUSH("ГолубойСКраснымОттенком", "LavenderBlush", 255, 240, 245),
    LIGHT_STEEL_BLUE("ГолубойСоСтальнымОттенком", "LightSteelBlue", 176, 196, 222),
    SLATE_GRAY("ГрифельноСерый", "SlateGray", 112, 128, 144),
    SLATE_BLUE("ГрифельноСиний", "SlateBlue", 106, 90, 205),
    BURLY_WOOD("Древесный", "BurlyWood", 222, 184, 184),
    WHITE_SMOKE("ДымчатоБелый", "WhiteSmoke", 245, 245, 245),
    YELLOW_GREEN("ЖелтоЗеленый", "YellowGreen", 154, 205, 50),
    YELLOW("Желтый", "Yellow", 255, 255, 0),
    MOCCASIN("ЗамшаСветлый", "Moccasin", 255, 228, 181),
    LAWN_GREEN("ЗеленаяЛужайка", "LawnGreen", 124, 252, 0),
    CHARTREUSE("ЗеленоватоЖелтый", "Chartreuse", 127, 255, 0),
    LIME("ЗеленоватоЛимонный", "Lime", 0, 255, 0),
    GREEN_YELLOW("ЗеленоЖелтый", "GreenYellow", 173, 255, 47),
    GREEN("Зеленый", "Green", 0, 255, 0),
    FOREST_GREEN("ЗеленыйЛес", "ForestGreen", 34, 139, 34),
    GOLDENROD("Золотистый", "Goldenrod", 218, 165, 32),
    GOLD("Золотой", "Gold", 255, 215, 0),
    INDIGO("Индиго", "Indigo", 75, 0, 130),
    INDIAN_RED("Киноварь", "IndianRed", 205, 92, 92),
    FIRE_BRICK("Кирпичный", "FireBrick", 178, 34, 34),
    SADDLE_BROWN("КожаноКоричневый", "SaddleBrown", 139, 69, 19),
    CORAL("Коралловый", "Coral", 255, 127, 80),
    BROWN("Коричневый", "Brown", 165, 42, 42),
    ROYAL_BLUE("КоролевскиГолубой", "RoyalBlue", 65, 105, 225),
    VIOLET_RED("КрасноФиолетовый", "VioletRed", 208, 32, 144),
    RED("Красный", "Red", 255, 0, 0),
    CREAM("Кремовый", "Cream", 255, 251, 240),
    AZURE("Лазурный", "Azure", 240, 255, 255),
    LIME_GREEN("ЛимонноЗеленый", "LimeGreen", 50, 205, 50),
    LEMON_CHIFFON("Лимонный", "LemonChiffon", 255, 250, 205),
    SALMON("Лосось", "Salmon", 250, 128, 114),
    LIGHT_SALMON("ЛососьСветлый", "LightSalmon", 255, 160, 122),
    DARK_SALMON("ЛососьТемный", "DarkSalmon", 233, 150, 122),
    LINEN("Льняной", "Linen", 250, 240, 230),
    CRIMSON("Малиновый", "Crimson", 220, 20, 60),
    MINT_CREAM("МятныйКрем", "MintCream", 245, 255, 250),
    NAVAJO_WHITE("НавахоБелый", "NavajoWhite", 255, 222, 173),
    DEEP_SKY_BLUE("НасыщенноНебесноГолубой", "DeepSkyBlue", 0, 191, 255),
    DEEP_PINK("НасыщенноРозовый", "DeepPink", 255, 20, 147),
    SKY_BLUE("НебесноГолубой", "SkyBlue", 135, 206, 235),
    MEDIUM_AQUA_MARINE("НейтральноАквамариновый", "MediumAquaMarine", 102, 205, 170),
    MEDIUM_TURQUOISE("НейтральноБирюзовый", "MediumTurquoise", 72, 209, 204),
    MEDIUM_SLATE_BLUE("НейтральноГрифельноСиний", "MediumSlateBlue", 123, 104, 238),
    MEDIUM_GREEN("НейтральноЗеленый", "MediumGreen", 192, 220, 192),
    PERU("НейтральноКоричневый", "Peru", 205, 133, 63),
    MEDIUM_PURPLE("НейтральноПурпурный", "MediumPurple", 147, 112, 219),
    MEDIUM_BLUE("НейтральноСиний", "MediumBlue", 0, 0, 205),
    MEDIUM_VIOLET_RED("НейтральноФиолетовоКрасный", "MediumVioletRed", 199, 21, 133),
    ORANGE_RED("ОранжевоКрасный", "OrangeRed", 255, 69, 0),
    ORANGE("Оранжевый", "Orange", 255, 165, 0),
    ORCHID("Орхидея", "Orchid", 218, 112, 214),
    MEDIUM_ORCHID("ОрхидеяНейтральный", "MediumOrchid", 186, 85, 211),
    DARK_ORCHID("ОрхидеяТемный", "DarkOrchid", 153, 50, 204),
    SIENNA("Охра", "Sienna", 160, 82, 45),
    PEACH_PUFF("Персиковый", "PeachPuff", 255, 218, 185),
    SANDY_BROWN("ПесочноКоричневый", "SandyBrown", 244, 164, 96),
    MIDNIGHT_BLUE("ПолночноСиний", "MidnightBlue", 25, 25, 112),
    GHOST_WHITE("ПризрачноБелый", "GhostWhite", 248, 248, 255),
    PURPLE("Пурпурный", "Purple", 160, 32, 240),
    WHEAT("Пшеничный", "Wheat", 245, 222, 179),
    ROSY_BROWN("РозовоКоричневый", "RosyBrown", 188, 143, 143),
    PINK("Розовый", "Pink", 255, 192, 203),
    TAN("РыжеватоКоричневый", "Tan", 210, 180, 140),
    LIGHT_SLATE_GRAY("СветлоГрифельноСерый", "LightSlateGray", 119, 136, 153),
    LIGHT_SLATE_BLUE("СветлоГрифельноСиний", "LightSlateBlue", 132, 112, 255),
    LIGHT_YELLOW("СветлоЖелтый", "LightYellow", 255, 255, 224),
    LIGHT_GREEN("СветлоЗеленый", "LightGreen", 144, 238, 144),
    LIGHT_GOLDEN_ROD("СветлоЗолотистый", "LightGoldenRod", 238, 221, 130),
    LIGHT_CORAL("СветлоКоралловый", "LightCoral", 240, 128, 128),
    BISQUE("СветлоКоричневый", "Bisque", 255, 228, 196),
    LIGHT_SKY_BLUE("СветлоНебесноГолубой", "LightSkyBlue", 135, 206, 250),
    LIGHT_PINK("СветлоРозовый", "LightPink", 255, 182, 193),
    LIGHT_GRAY("СветлоСерый", "LightGray", 211, 211, 211),
    GAINSBORO("СеребристоСерый", "Gainsboro", 220, 220, 220),
    CADET_BLUE("СероСиний", "CadetBlue", 95, 158, 160),
    DODGER_BLUE("СинеСерый", "DodgerBlue", 30, 144, 255),
    BLUE_VIOLET("СинеФиолетовый", "BlueViolet", 138, 43, 226),
    BLUE("Синий", "Blue", 0, 0, 255),
    STEEL_BLUE("СинийСоСтальнымОттенком", "SteelBlue", 70, 130, 180),
    POWDER_BLUE("СинийСПороховымОттенком", "PowderBlue", 176, 224, 230),
    PLUM("Сливовый", "Plum", 221, 160, 221),
    IVORY("СлоноваяКость", "Ivory", 255, 255, 240),
    OLD_LACE("СтароеКружево", "OldLace", 253, 245, 230),
    DARK_TURQUOISE("ТемноБирюзовый", "DarkTurquoise", 0, 206, 209),
    MAROON("ТемноБордовый", "Maroon", 176, 48, 96),
    DARK_SLATE_GRAY("ТемноГрифельноСерый", "DarkSlateGray", 47, 79, 79),
    DARK_SLATE_BLUE("ТемноГрифельноСиний", "DarkSlateBlue", 72, 61, 139),
    DARK_GREEN("ТемноЗеленый", "DarkGreen", 0, 100, 0),
    DARK_RED("ТемноКрасный", "DarkRed", 139, 0, 0),
    DARK_OLIVE_GREEN("ТемноОливковоЗеленый", "DarkOliveGreen", 85, 107, 47),
    DARK_ORANGE("ТемноОранжевый", "DarkOrange", 255, 140, 0),
    DARK_BLUE("ТемноСиний", "DarkBlue", 0, 0, 139),
    DARK_VIOLET("ТемноФиолетовый", "DarkViolet", 148, 0, 211),
    HOT_PINK("ТеплоРозовый", "HotPink", 255, 105, 180),
    TOMATO("Томатный", "Tomato", 255, 99, 71),
    PAPAYA_WHIP("ТопленоеМолоко", "PapayaWhip", 255, 239, 213),
    MISTY_ROSE("ТусклоРозовый", "MistyRose", 255, 228, 225),
    VIOLET("Фиолетовый", "Violet", 238, 130, 238),
    MAGENTA("Фуксин", "Magenta", 255, 0, 255),
    DARK_MAGENTA("ФуксинТемный", "DarkMagenta", 139, 0, 139),
    DARK_KHAKI("ХакиТемный", "DarkKhaki", 189, 183, 107),
    MEDIUM_SEA_GREEN("ЦветМорскойВолныНейтральный", "MediumSeaGreen", 60, 179, 113),
    LIGHT_SEA_GREEN("ЦветМорскойВолныСветлый", "LightSeaGreen", 32, 178, 170),
    DARK_SEA_GREEN("ЦветМорскойВолныТемный", "DarkSeaGreen", 143, 188, 143),
    FLORAL_WHITE("ЦветокБелый", "FloralWhite", 255, 250, 240),
    CYAN("Циан", "Cyan", 0, 255, 255),
    LIGHT_CYAN("ЦианСветлый", "LightCyan", 224, 255, 255),
    DARK_CYAN("ЦианТемный", "DarkCyan", 0, 139, 139),
    BLACK("Черный", "Black", 0, 0, 0),
    CHOCOLATE("Шоколадный", "Chocolate", 210, 105, 30),

    MEDIUM_GRAY("НейтральноСерый", "MediumGray", 0, 0, 0),
    OLIVE("Оливковый", "Olive", 0, 0, 0),
    SEA_SHELL("Перламутровый", "SeaShell", 0, 0, 0),
    HONEY_DEW("Роса", "HoneyDew", 0, 0, 0),
    SILVER("Серебряный", "Silver", 0, 0, 0),
    GRAY("Серый", "Gray", 0, 0, 0),
    DARK_GOLDEN_ROD("ТемноЗолотистый", "DarkGoldenRod", 0, 0, 0),
    DARK_GRAY("ТемноСерый", "DarkGray", 0, 0, 0),
    OLIVEDRAB("ТусклоОливковый", "Olivedrab", 0, 0, 0),
    DIM_GRAY("ТусклоСерый", "DimGray", 0, 0, 0),
    NAVY("Ультрамарин", "Navy", 0, 0, 0),
    FUCHSIA("Фуксия", "Fuchsia", 0, 0, 0),
    KHAKI("Хаки", "Khaki", 0, 0, 0),
    SEAGREEN("ЦветМорскойВолны", "Seagreen", 0, 0, 0),
    AQUA("ЦианАкварельный", "Aqua", 0, 0, 0),
    TEAL("ЦианНейтральный", "Teal", 0, 0, 0),
    CORN_SILK("ШелковыйОттенок", "CornSilk", 0, 0, 0),
    MEDIUM_SPRING_GREEN("НейтральноВесеннеЗеленый", "MediumSpringGreen", 0, 0, 0),
    LIGHT_GOLDEN_ROD_YELLOW("СветлоЖелтыйЗолотистый", "LightGoldenRodYellow", 0, 0, 0);

    private final String ru;
    private final String en;
    private final int red;
    private final int green;
    private final int blue;

    public static Optional<WebColor> findByColor(int red, int green, int blue) {
      for (WebColor color : values()) {
        if (color.getRed() == red && color.getGreen() == green && color.getBlue() == blue) {
          return Optional.of(color);
        }
      }

      return Optional.empty();
    }
  }
}
