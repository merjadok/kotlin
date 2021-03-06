/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.k2js.translate.reference;

import com.google.dart.compiler.backend.js.ast.JsExpression;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jet.lang.psi.JetCallExpression;
import org.jetbrains.jet.lang.psi.JetExpression;
import org.jetbrains.jet.lang.psi.JetQualifiedExpression;
import org.jetbrains.jet.lang.psi.JetSimpleNameExpression;
import org.jetbrains.k2js.translate.context.TranslationContext;

import static org.jetbrains.k2js.translate.general.Translation.translateAsExpression;
import static org.jetbrains.k2js.translate.utils.PsiUtils.getNotNullSimpleNameSelector;
import static org.jetbrains.k2js.translate.utils.PsiUtils.getSelector;

/**
 * @author Pavel Talanov
 */
public final class QualifiedExpressionTranslator {

    private QualifiedExpressionTranslator() {
    }

    @NotNull
    public static AccessTranslator getAccessTranslator(@NotNull JetQualifiedExpression expression,
                                                       @NotNull TranslationContext context) {

        JsExpression receiver = translateReceiver(expression, context);
        PropertyAccessTranslator result =
                PropertyAccessTranslator.newInstance(getNotNullSimpleNameSelector(expression), receiver,
                        CallType.getCallTypeForQualifiedExpression(expression), context);
        result.setCallType(CallType.getCallTypeForQualifiedExpression(expression));
        return result;
    }

    @NotNull
    public static JsExpression translateQualifiedExpression(@NotNull JetQualifiedExpression expression,
                                                            @NotNull TranslationContext context) {
        JsExpression receiver = translateReceiver(expression, context);
        JetExpression selector = getSelector(expression);
        CallType callType = CallType.getCallTypeForQualifiedExpression(expression);
        return dispatchToCorrectTranslator(receiver, selector, callType, context);
    }

    @NotNull
    private static JsExpression dispatchToCorrectTranslator(@NotNull JsExpression receiver,
                                                            @NotNull JetExpression selector,
                                                            @NotNull CallType callType,
                                                            @NotNull TranslationContext context) {
        if (PropertyAccessTranslator.canBePropertyGetterCall(selector, context)) {
            assert selector instanceof JetSimpleNameExpression : "Selectors for properties must be simple names.";
            return PropertyAccessTranslator.translateAsPropertyGetterCall
                    ((JetSimpleNameExpression) selector, receiver, callType, context);
        }
        if (selector instanceof JetCallExpression) {
            return CallExpressionTranslator.translate((JetCallExpression) selector, receiver, callType, context);
        }
        throw new AssertionError("Unexpected qualified expression");
    }

    //TODO: if has duplications
    @NotNull
    private static JsExpression translateReceiver(@NotNull JetQualifiedExpression expression,
                                                  @NotNull TranslationContext context) {
        return translateAsExpression(expression.getReceiverExpression(), context);
    }
}
