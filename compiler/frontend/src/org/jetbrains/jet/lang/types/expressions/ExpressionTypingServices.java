/*
 * Copyright 2010-2012 JetBrains s.r.o.
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

package org.jetbrains.jet.lang.types.expressions;

import com.google.common.collect.Lists;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jet.lang.JetSemanticServices;
import org.jetbrains.jet.lang.descriptors.DeclarationDescriptor;
import org.jetbrains.jet.lang.descriptors.FunctionDescriptor;
import org.jetbrains.jet.lang.descriptors.FunctionDescriptorUtil;
import org.jetbrains.jet.lang.descriptors.VariableDescriptor;
import org.jetbrains.jet.lang.diagnostics.Diagnostic;
import org.jetbrains.jet.lang.psi.*;
import org.jetbrains.jet.lang.resolve.*;
import org.jetbrains.jet.lang.resolve.calls.autocasts.DataFlowInfo;
import org.jetbrains.jet.lang.resolve.scopes.JetScope;
import org.jetbrains.jet.lang.resolve.scopes.WritableScope;
import org.jetbrains.jet.lang.resolve.scopes.WritableScopeImpl;
import org.jetbrains.jet.lang.types.CommonSupertypes;
import org.jetbrains.jet.lang.types.ErrorUtils;
import org.jetbrains.jet.lang.types.JetStandardClasses;
import org.jetbrains.jet.lang.types.JetType;
import org.jetbrains.jet.lexer.JetTokens;

import java.util.*;

import static org.jetbrains.jet.lang.diagnostics.Errors.TYPE_MISMATCH;
import static org.jetbrains.jet.lang.resolve.BindingContext.LABEL_TARGET;
import static org.jetbrains.jet.lang.resolve.BindingContext.STATEMENT;
import static org.jetbrains.jet.lang.types.TypeUtils.FORBIDDEN;
import static org.jetbrains.jet.lang.types.TypeUtils.NO_EXPECTED_TYPE;

/**
* @author abreslav
*/
public class ExpressionTypingServices {
    private final JetSemanticServices semanticServices;
    private final BindingTrace trace;

    private final ExpressionTypingFacade expressionTypingFacade = ExpressionTypingVisitorDispatcher.create();

    public ExpressionTypingServices(JetSemanticServices semanticServices, BindingTrace trace) {
        this.semanticServices = semanticServices;
        this.trace = trace;
    }

    @NotNull
    public JetType safeGetType(@NotNull JetScope scope, @NotNull JetExpression expression, @NotNull JetType expectedType) {
        return safeGetType(scope, expression, expectedType, DataFlowInfo.EMPTY);
    }

    public JetType safeGetType(@NotNull JetScope scope, @NotNull JetExpression expression, @NotNull JetType expectedType, @NotNull DataFlowInfo dataFlowInfo) {
        JetType type = getType(scope, expression, expectedType, dataFlowInfo);
        if (type != null) {
            return type;
        }
        return ErrorUtils.createErrorType("Type for " + expression.getText());
    }

    @Nullable
    public JetType getType(@NotNull final JetScope scope, @NotNull JetExpression expression, @NotNull JetType expectedType) {
        return getType(scope, expression, expectedType, DataFlowInfo.EMPTY);
    }

    @Nullable
    public JetType getType(@NotNull final JetScope scope, @NotNull JetExpression expression, @NotNull JetType expectedType, @NotNull DataFlowInfo dataFlowInfo) {
        ExpressionTypingContext context = ExpressionTypingContext.newContext(
                expression.getProject(),
                semanticServices,
                new HashMap<JetPattern, DataFlowInfo>(), new HashMap<JetPattern, List<VariableDescriptor>>(), new LabelResolver(),
                trace, scope, dataFlowInfo, expectedType, FORBIDDEN, false
        );
        return expressionTypingFacade.getType(expression, context);
    }

    public JetType getTypeWithNamespaces(@NotNull final JetScope scope, @NotNull JetExpression expression) {
        ExpressionTypingContext context = ExpressionTypingContext.newContext(
                expression.getProject(),
                semanticServices,
                new HashMap<JetPattern, DataFlowInfo>(), new HashMap<JetPattern, List<VariableDescriptor>>(), new LabelResolver(),
                trace, scope, DataFlowInfo.EMPTY, NO_EXPECTED_TYPE, FORBIDDEN,
                true);
        return expressionTypingFacade.getType(expression, context);
//        return ((ExpressionTypingContext) ExpressionTyperVisitorWithNamespaces).INSTANCE.getType(expression, ExpressionTypingContext.newRootContext(semanticServices, trace, scope, DataFlowInfo.getEmpty(), TypeUtils.NO_EXPECTED_TYPE, TypeUtils.NO_EXPECTED_TYPE));
    }

    @NotNull
    public JetType inferFunctionReturnType(@NotNull JetScope outerScope, JetDeclarationWithBody function, FunctionDescriptor functionDescriptor) {
        Map<JetExpression, JetType> typeMap = collectReturnedExpressionsWithTypes(trace, outerScope, function, functionDescriptor);
        Collection<JetType> types = typeMap.values();
        return types.isEmpty()
               ? JetStandardClasses.getNothingType()
               : CommonSupertypes.commonSupertype(types);
    }


    public void checkFunctionReturnType(@NotNull JetScope functionInnerScope, @NotNull JetDeclarationWithBody function, @NotNull FunctionDescriptor functionDescriptor) {
        checkFunctionReturnType(functionInnerScope, function, functionDescriptor, DataFlowInfo.EMPTY, null);
    }

    public void checkFunctionReturnType(@NotNull JetScope functionInnerScope, @NotNull JetDeclarationWithBody function, @NotNull FunctionDescriptor functionDescriptor, @Nullable JetType expectedReturnType) {
        checkFunctionReturnType(functionInnerScope, function, functionDescriptor, DataFlowInfo.EMPTY, expectedReturnType);
    }
/////////////////////////////////////////////////////////

    /*package*/ void checkFunctionReturnType(@NotNull JetScope functionInnerScope, @NotNull JetDeclarationWithBody function, @NotNull FunctionDescriptor functionDescriptor, @NotNull DataFlowInfo dataFlowInfo) {
        checkFunctionReturnType(functionInnerScope, function, functionDescriptor, dataFlowInfo, null);
    }

    /*package*/ void checkFunctionReturnType(@NotNull JetScope functionInnerScope, @NotNull JetDeclarationWithBody function, @NotNull FunctionDescriptor functionDescriptor, @NotNull DataFlowInfo dataFlowInfo, @Nullable JetType expectedReturnType) {
        if (expectedReturnType == null) {
            expectedReturnType = functionDescriptor.getReturnType();
            if (!function.hasBlockBody() && !function.hasDeclaredReturnType()) {
                expectedReturnType = NO_EXPECTED_TYPE;
            }
        }
        checkFunctionReturnType(function, ExpressionTypingContext.newContext(
                function.getProject(),
                semanticServices, new HashMap<JetPattern, DataFlowInfo>(), new HashMap<JetPattern, List<VariableDescriptor>>(), new LabelResolver(),
                trace, functionInnerScope, dataFlowInfo, NO_EXPECTED_TYPE, expectedReturnType, false
        ));
    }

    /*package*/ void checkFunctionReturnType(JetDeclarationWithBody function, ExpressionTypingContext context) {
        JetExpression bodyExpression = function.getBodyExpression();
        if (bodyExpression == null) return;

        final boolean blockBody = function.hasBlockBody();
        final ExpressionTypingContext newContext =
                blockBody
                ? context.replaceExpectedType(NO_EXPECTED_TYPE)
                : context.replaceExpectedType(context.expectedReturnType == FORBIDDEN ? NO_EXPECTED_TYPE : context.expectedReturnType).replaceExpectedReturnType(FORBIDDEN);

        if (function instanceof JetFunctionLiteralExpression) {
            JetFunctionLiteralExpression functionLiteralExpression = (JetFunctionLiteralExpression) function;
            JetBlockExpression blockExpression = functionLiteralExpression.getBodyExpression();
            assert blockExpression != null;
            getBlockReturnedType(newContext.scope, blockExpression, CoercionStrategy.COERCION_TO_UNIT, context);
        }
        else {
            expressionTypingFacade.getType(bodyExpression, newContext, !blockBody);
        }
    }

    @Nullable
    /*package*/ JetType getBlockReturnedType(@NotNull JetScope outerScope, @NotNull JetBlockExpression expression, @NotNull CoercionStrategy coercionStrategyForLastExpression, ExpressionTypingContext context) {
        List<JetElement> block = expression.getStatements();
        if (block.isEmpty()) {
            return DataFlowUtils.checkType(JetStandardClasses.getUnitType(), expression, context);
        }

        DeclarationDescriptor containingDescriptor = outerScope.getContainingDeclaration();
        WritableScope scope = new WritableScopeImpl(outerScope, containingDescriptor, new TraceBasedRedeclarationHandler(context.trace)).setDebugName("getBlockReturnedType");
        scope.changeLockLevel(WritableScope.LockLevel.BOTH);
        return getBlockReturnedTypeWithWritableScope(scope, block, coercionStrategyForLastExpression, context);
    }

    private Map<JetExpression, JetType> collectReturnedExpressionsWithTypes(
            final @NotNull BindingTrace trace,
            JetScope outerScope,
            final JetDeclarationWithBody function,
            FunctionDescriptor functionDescriptor) {
        JetExpression bodyExpression = function.getBodyExpression();
        assert bodyExpression != null;
        JetScope functionInnerScope = FunctionDescriptorUtil.getFunctionInnerScope(outerScope, functionDescriptor, trace);
        expressionTypingFacade.getType(bodyExpression, ExpressionTypingContext.newContext(function.getProject(), semanticServices, new HashMap<JetPattern, DataFlowInfo>(), new HashMap<JetPattern, List<VariableDescriptor>>(), new LabelResolver(),
                                                                                          trace, functionInnerScope, DataFlowInfo.EMPTY, NO_EXPECTED_TYPE, FORBIDDEN, false), !function.hasBlockBody());
        //todo function literals
        final Collection<JetExpression> returnedExpressions = Lists.newArrayList();
        if (function.hasBlockBody()) {
            //now this code is never invoked!, it should be invoked for inference of return type of function literal with local returns
            bodyExpression.accept(new JetTreeVisitor<JetDeclarationWithBody>() {
                @Override
                public Void visitReturnExpression(JetReturnExpression expression, JetDeclarationWithBody outerFunction) {
                    JetSimpleNameExpression targetLabel = expression.getTargetLabel();
                    PsiElement element = targetLabel != null ? trace.get(LABEL_TARGET, targetLabel) : null;
                    if (element == function || (targetLabel == null && outerFunction == function)) {
                        returnedExpressions.add(expression);
                    }
                    return null;
                }

                @Override
                public Void visitFunctionLiteralExpression(JetFunctionLiteralExpression expression, JetDeclarationWithBody outerFunction) {
                    return super.visitFunctionLiteralExpression(expression, expression.getFunctionLiteral());
                }

                @Override
                public Void visitNamedFunction(JetNamedFunction function, JetDeclarationWithBody outerFunction) {
                    return super.visitNamedFunction(function, function);
                }
            }, function);
        }
        else {
            returnedExpressions.add(bodyExpression);
        }
        Map<JetExpression, JetType> typeMap = new HashMap<JetExpression, JetType>();
        for (JetExpression returnedExpression : returnedExpressions) {
            JetType cachedType = trace.getBindingContext().get(BindingContext.EXPRESSION_TYPE, returnedExpression);
            trace.record(STATEMENT, returnedExpression, false);
            if (cachedType != null) {
                typeMap.put(returnedExpression, cachedType);
            } 
            else {
                typeMap.put(returnedExpression, ErrorUtils.createErrorType("Error function type"));
            }
        }
        return typeMap;
    }

    /*package*/ JetType getBlockReturnedTypeWithWritableScope(@NotNull WritableScope scope, @NotNull List<? extends JetElement> block, @NotNull CoercionStrategy coercionStrategyForLastExpression, ExpressionTypingContext context) {
        if (block.isEmpty()) {
            return JetStandardClasses.getUnitType();
        }

        ExpressionTypingInternals blockLevelVisitor = ExpressionTypingVisitorDispatcher.createForBlock(scope);
        ExpressionTypingContext newContext = createContext(context, trace, scope, context.dataFlowInfo, NO_EXPECTED_TYPE, context.expectedReturnType);

        JetType result = null;
        for (Iterator<? extends JetElement> iterator = block.iterator(); iterator.hasNext(); ) {
            final JetElement statement = iterator.next();
            trace.record(STATEMENT, statement);
            final JetExpression statementExpression = (JetExpression) statement;
            //TODO constructor assert context.expectedType != FORBIDDEN : ""
            if (!iterator.hasNext()) {
                if (context.expectedType != NO_EXPECTED_TYPE) {
                    if (coercionStrategyForLastExpression == CoercionStrategy.COERCION_TO_UNIT && JetStandardClasses.isUnit(context.expectedType)) {
                        // This implements coercion to Unit
                        TemporaryBindingTrace temporaryTraceExpectingUnit = TemporaryBindingTrace.create(trace);
                        final boolean[] mismatch = new boolean[1];
                        ObservableBindingTrace errorInterceptingTrace = makeTraceInterceptingTypeMismatch(temporaryTraceExpectingUnit, statementExpression, mismatch);
                        newContext = createContext(newContext, errorInterceptingTrace, scope, newContext.dataFlowInfo, context.expectedType, context.expectedReturnType);
                        result = blockLevelVisitor.getType(statementExpression, newContext, true);
                        if (mismatch[0]) {
                            TemporaryBindingTrace temporaryTraceNoExpectedType = TemporaryBindingTrace.create(trace);
                            mismatch[0] = false;
                            ObservableBindingTrace interceptingTrace = makeTraceInterceptingTypeMismatch(temporaryTraceNoExpectedType, statementExpression, mismatch);
                            newContext = createContext(newContext, interceptingTrace, scope, newContext.dataFlowInfo, NO_EXPECTED_TYPE, context.expectedReturnType);
                            result = blockLevelVisitor.getType(statementExpression, newContext, true);
                            if (mismatch[0]) {
                                temporaryTraceExpectingUnit.commit();
                            }
                            else {
                                temporaryTraceNoExpectedType.commit();
                            }
                        }
                        else {
                            temporaryTraceExpectingUnit.commit();
                        }
                    }
                    else {
                        newContext = createContext(newContext, trace, scope, newContext.dataFlowInfo, context.expectedType, context.expectedReturnType);
                        result = blockLevelVisitor.getType(statementExpression, newContext, true);
                    }
                }
                else {
                    result = blockLevelVisitor.getType(statementExpression, newContext, true);
                    if (coercionStrategyForLastExpression == CoercionStrategy.COERCION_TO_UNIT) {
                        boolean mightBeUnit = false;
                        if (statementExpression instanceof JetDeclaration) {
                            mightBeUnit = true;
                        }
                        if (statementExpression instanceof JetBinaryExpression) {
                            JetBinaryExpression binaryExpression = (JetBinaryExpression) statementExpression;
                            IElementType operationType = binaryExpression.getOperationToken();
                            if (operationType == JetTokens.EQ || OperatorConventions.ASSIGNMENT_OPERATIONS.containsKey(operationType)) {
                                mightBeUnit = true;
                            }
                        }
                        if (mightBeUnit) {
                            // ExpressionTypingVisitorForStatements should return only null or Unit for declarations and assignments
                            assert result == null || JetStandardClasses.isUnit(result);
                            result = JetStandardClasses.getUnitType();
                        }
                    }
                }
            }
            else {
                result = blockLevelVisitor.getType(statementExpression, newContext, true);
            }

            DataFlowInfo newDataFlowInfo = blockLevelVisitor.getResultingDataFlowInfo();
            if (newDataFlowInfo == null) {
                newDataFlowInfo = context.dataFlowInfo;
            }
            if (newDataFlowInfo != context.dataFlowInfo) {
                newContext = createContext(newContext, trace, scope, newDataFlowInfo, NO_EXPECTED_TYPE, context.expectedReturnType);
            }
            blockLevelVisitor = ExpressionTypingVisitorDispatcher.createForBlock(scope);
        }
        return result;
    }

    private ExpressionTypingContext createContext(ExpressionTypingContext oldContext, BindingTrace trace, WritableScope scope, DataFlowInfo dataFlowInfo, JetType expectedType, JetType expectedReturnType) {
        return ExpressionTypingContext.newContext(oldContext.project, oldContext.semanticServices, oldContext.patternsToDataFlowInfo, oldContext.patternsToBoundVariableLists, oldContext.labelResolver, trace, scope, dataFlowInfo, expectedType, expectedReturnType, oldContext.namespacesAllowed);
    }

    private ObservableBindingTrace makeTraceInterceptingTypeMismatch(final BindingTrace trace, final JetExpression expressionToWatch, final boolean[] mismatchFound) {
        return new ObservableBindingTrace(trace) {

            @Override
            public void report(@NotNull Diagnostic diagnostic) {
                if (diagnostic.getFactory() == TYPE_MISMATCH && diagnostic.getPsiElement() == expressionToWatch) {
                    mismatchFound[0] = true;
                }
                super.report(diagnostic);
            }
        };
    }
}
