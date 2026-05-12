// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.codeinsights.impl.base

import com.intellij.codeInspection.CleanupLocalInspectionTool
import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemHighlightType.GENERIC_ERROR_OR_WARNING
import com.intellij.codeInspection.ProblemHighlightType.INFORMATION
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.options.OptPane
import com.intellij.codeInspection.options.OptPane.checkbox
import com.intellij.codeInspection.options.OptPane.pane
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiRecursiveElementVisitor
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.createSmartPointer
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import org.jetbrains.kotlin.idea.base.psi.getLineNumber
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle
import org.jetbrains.kotlin.idea.codeinsight.api.applicable.inspections.KotlinModCommandQuickFix
import org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection
import org.jetbrains.kotlin.idea.codeinsight.utils.negate
import org.jetbrains.kotlin.idea.util.PsiPrecedences
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtAnnotatedExpression
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtContainerNodeForControlStructureBody
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtIfExpression
import org.jetbrains.kotlin.psi.KtIsExpression
import org.jetbrains.kotlin.psi.KtLabeledExpression
import org.jetbrains.kotlin.psi.KtParenthesizedExpression
import org.jetbrains.kotlin.psi.KtPrefixExpression
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtPsiUtil
import org.jetbrains.kotlin.psi.KtReturnExpression
import org.jetbrains.kotlin.psi.createExpressionByPattern
import org.jetbrains.kotlin.psi.ifExpressionVisitor
import org.jetbrains.kotlin.psi.psiUtil.anyDescendantOfType
import org.jetbrains.kotlin.psi.psiUtil.getNextSiblingIgnoringWhitespaceAndComments
import org.jetbrains.kotlin.psi.psiUtil.getPrevSiblingIgnoringWhitespaceAndComments
import org.jetbrains.kotlin.psi.psiUtil.nextLeafs
import org.jetbrains.kotlin.psi.psiUtil.prevLeafs

/**
 * A parent class for K1 and K2 RedundantIfInspection.
 *
 * This class contains most parts of RedundantIfInspection that are common between K1 and K2.
 * The only things K1 and K2 RedundantIfInspection have to implement are the boolean-type checks
 * that return whether the given KtExpression is a boolean expression. Since these checks use the
 * type analysis, K1/K2 must have different implementations.
 */
abstract class RedundantIfInspectionBase : AbstractKotlinInspection(), CleanupLocalInspectionTool {

    private data class IfExpressionRedundancyInfo(
        val replacementStrategy: ReplacementStrategy,
        val branchType: BranchType,
        val branchExpressionPointer: SmartPsiElementPointer<KtExpression>?,
        val returnAfterIf: KtExpression?,
    ) {
        constructor(
            replacementStrategy: ReplacementStrategy,
            branchType: BranchType,
            branchExpression: KtExpression?,
            returnAfterIf: KtExpression?
        ) : this(replacementStrategy, branchType, branchExpression?.createSmartPointer(), returnAfterIf)
    }

    private enum class ReplacementStrategy(val operationText: String?, val negateCondition: Boolean) {
        CONDITION(null, false),
        NEGATED_CONDITION(null, true),
        CONDITION_OR_BRANCH("||", false),
        NEGATED_CONDITION_AND_BRANCH("&&", true),
        NEGATED_CONDITION_OR_BRANCH("||", true),
        CONDITION_AND_BRANCH("&&", false),
    }

    @JvmField
    var ignoreChainedIf: Boolean = true

    override fun getOptionsPane(): OptPane {
        return pane(
            checkbox("ignoreChainedIf", KotlinBundle.message("redundant.if.option.ignore.chained")),
        )
    }

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean, session: LocalInspectionToolSession): PsiElementVisitor {
        return ifExpressionVisitor { expression ->
            if (expression.condition == null) return@ifExpressionVisitor
            val (replacementStrategy, branchType, branchExpressionPointer, returnAfterIf) = expression.redundancyInfo() ?: return@ifExpressionVisitor

            val isChainedIf = expression.getPrevSiblingIgnoringWhitespaceAndComments() is KtIfExpression ||
                    expression.parent.let { it is KtContainerNodeForControlStructureBody && it.expression == expression }

            val hasConditionWithFloatingPointType = expression.hasConditionWithFloatingPointType()
            val bothBranchesHaveComments = bothBranchesHaveComments(expression.then, expression.`else`, returnAfterIf)
            val highlightType =
                if ((isChainedIf && ignoreChainedIf) || hasConditionWithFloatingPointType || bothBranchesHaveComments) INFORMATION
                else GENERIC_ERROR_OR_WARNING

            holder.registerProblemWithoutOfflineInformation(
                expression,
                KotlinBundle.message("redundant.if.statement"),
                isOnTheFly,
                highlightType,
                expression.ifKeyword.textRangeInParent,
                RemoveRedundantIf(replacementStrategy, branchType, branchExpressionPointer, returnAfterIf, hasConditionWithFloatingPointType)
            )
        }
    }

    /**
     * Tells whether the given [expression] is of the boolean type.
     *
     * Called from read action and from modal window, so it's safe to use resolve here.
     */
    abstract fun isBooleanExpression(expression: KtExpression): Boolean

    /**
     * Tells whether the given [expression] is of the non-nullable boolean type.
     */
    abstract fun isNotNullableBooleanExpression(expression: KtExpression): Boolean

    abstract fun invertEmptinessCheck(condition: KtExpression): KtExpression?

    abstract fun KtIfExpression.hasConditionWithFloatingPointType(): Boolean

    protected fun KtIfExpression.inequalityCondition(): KtBinaryExpression? {
        return (condition as? KtBinaryExpression)
            ?.takeIf { it.left != null && it.right != null }
            ?.takeIf {
                val operation = it.operationToken
                operation == KtTokens.LT || operation == KtTokens.LTEQ || operation == KtTokens.GT || operation == KtTokens.GTEQ
            }
    }

    private fun bothBranchesHaveComments(
        thenExpression: KtExpression?,
        elseExpression: KtExpression?,
        returnAfterIf: KtExpression?
    ): Boolean =
        thenExpression.hasComments() &&
            (elseExpression.hasComments(thenExpression) || returnAfterIf.hasComments(thenExpression))

    private fun PsiElement?.hasComments(prevExpression: KtExpression? = null): Boolean {
        fun Sequence<PsiElement>.comments(): Sequence<PsiComment> =
            takeWhile { it is PsiWhiteSpace || it is PsiComment }.filterIsInstance<PsiComment>()

        if (this == null) return false

        val lineNumber = getLineNumber()
        val prevExpressionLineNumber = prevExpression?.getLineNumber()
        val hasPrevComment = prevLeafs.comments().any { it.getLineNumber() != prevExpressionLineNumber }
        val ifExpressionHasPrevComment = (parent?.parent as? KtIfExpression)?.prevLeafs?.comments()?.any() == true
        val hasTailComment = nextLeafs.comments().any { it.getLineNumber() == lineNumber }

        return hasPrevComment || ifExpressionHasPrevComment || hasTailComment || anyDescendantOfType<PsiComment>()
    }

    private sealed class BranchType {
        object Simple : BranchType()

        object Return : BranchType()

        data class LabeledReturn(val label: String) : BranchType()

        class Assign(left: KtExpression) : BranchType() {
            val lvalue: SmartPsiElementPointer<KtExpression> = left.let(SmartPointerManager::createPointer)

            override fun equals(other: Any?) = other is Assign && lvalue.element?.text == other.lvalue.element?.text

            override fun hashCode() = lvalue.element?.text.hashCode()
        }
    }

    private fun KtIfExpression.redundancyInfo(): IfExpressionRedundancyInfo? {
        val (thenReturn, thenType) = then?.getBranchExpression() ?: return null
        val elseOrReturnAfterIf = `else` ?:
            // When the target if-expression does not have an else expression and the next expression is a return expression,
            // we can consider it as an else expression. For example,
            //     fun foo(bar: String?):Boolean {
            //       if (bar == null) return false
            //       return true
            //     }
            returnAfterIf() ?: return null
        val (elseReturn, elseType) = elseOrReturnAfterIf.getBranchExpression() ?: return null
        if (thenType != elseType) return null

        val returnAfterIf = if (`else` == null) elseOrReturnAfterIf else null
        return when {
            KtPsiUtil.isTrueConstant(thenReturn) && KtPsiUtil.isFalseConstant(elseReturn) ->
                IfExpressionRedundancyInfo(
                    ReplacementStrategy.CONDITION,
                    thenType,
                    branchExpression = null,
                    returnAfterIf,
                )

            KtPsiUtil.isFalseConstant(thenReturn) && KtPsiUtil.isTrueConstant(elseReturn) ->
                IfExpressionRedundancyInfo(
                    ReplacementStrategy.NEGATED_CONDITION,
                    thenType,
                    branchExpression = null,
                    returnAfterIf,
                )

            KtPsiUtil.isTrueConstant(thenReturn) && elseReturn.isNonConstantBooleanExpression() ->
                IfExpressionRedundancyInfo(
                    ReplacementStrategy.CONDITION_OR_BRANCH,
                    thenType,
                    elseReturn,
                    returnAfterIf,
                )

            KtPsiUtil.isFalseConstant(thenReturn) && elseReturn.isNonConstantBooleanExpression() ->
                IfExpressionRedundancyInfo(
                    ReplacementStrategy.NEGATED_CONDITION_AND_BRANCH,
                    thenType,
                    elseReturn,
                    returnAfterIf,
                )

            thenReturn.isNonConstantBooleanExpression() && KtPsiUtil.isTrueConstant(elseReturn) ->
                IfExpressionRedundancyInfo(
                    ReplacementStrategy.NEGATED_CONDITION_OR_BRANCH,
                    thenType,
                    thenReturn,
                    returnAfterIf,
                )

            thenReturn.isNonConstantBooleanExpression() && KtPsiUtil.isFalseConstant(elseReturn) ->
                IfExpressionRedundancyInfo(
                    ReplacementStrategy.CONDITION_AND_BRANCH,
                    thenType,
                    thenReturn,
                    returnAfterIf,
                )

            else -> null
        }
    }

    private fun KtExpression.getBranchExpression(): Pair<KtExpression, BranchType>? {
        return when (this) {
            is KtReturnExpression -> {
                val branchType = labeledExpression?.let { BranchType.LabeledReturn(it.text) } ?: BranchType.Return
                returnedExpression?.let { it to branchType }
            }

            is KtBlockExpression -> {
                val branchExpression = statements.singleOrNull()?.getBranchExpression()
                branchExpression
            }
            is KtBinaryExpression -> {
                val left = left
                val right = right
                if (operationToken == KtTokens.EQ && left != null && right != null) {
                    right to BranchType.Assign(left)
                } else {
                    this to BranchType.Simple
                }
            }

            else -> {
                this to BranchType.Simple
            }
        }
    }

    private fun KtExpression.isNonConstantBooleanExpression(): Boolean {
        return !KtPsiUtil.isTrueConstant(this) &&
                !KtPsiUtil.isFalseConstant(this) &&
                (isObviouslyBooleanExpression() || isNotNullableBooleanExpression(this))
    }

    private fun KtExpression.isObviouslyBooleanExpression(): Boolean = when (this) {
        is KtAnnotatedExpression -> baseExpression?.isObviouslyBooleanExpression() == true
        is KtLabeledExpression -> baseExpression?.isObviouslyBooleanExpression() == true
        is KtParenthesizedExpression -> expression?.isObviouslyBooleanExpression() == true
        is KtPrefixExpression -> operationToken == KtTokens.EXCL
        is KtIsExpression -> true
        is KtBinaryExpression -> when (operationToken) {
            KtTokens.ANDAND,
            KtTokens.OROR,
            KtTokens.EQEQ,
            KtTokens.EXCLEQ,
            KtTokens.EQEQEQ,
            KtTokens.EXCLEQEQEQ,
            KtTokens.LT,
            KtTokens.LTEQ,
            KtTokens.GT,
            KtTokens.GTEQ,
            KtTokens.IN_KEYWORD,
            KtTokens.NOT_IN,
            -> true

            else -> false
        }

        else -> false
    }

    private inner class RemoveRedundantIf(
        private val replacementStrategy: ReplacementStrategy,
        private val branchType: BranchType,
        private val branchExpressionPointer: SmartPsiElementPointer<KtExpression>?,
        returnAfterIf: KtExpression?,
        private val mayChangeSemantics: Boolean,
    ) : KotlinModCommandQuickFix<KtIfExpression>() {
        private val returnExpressionAfterIfPointer: SmartPsiElementPointer<KtExpression>? = returnAfterIf?.let(SmartPointerManager::createPointer)

        override fun getFamilyName(): String =
            if (mayChangeSemantics) KotlinBundle.message("remove.redundant.if.may.change.semantics.with.floating.point.types")
            else KotlinBundle.message("remove.redundant.if.text")

        override fun applyFix(project: Project, element: KtIfExpression, updater: ModPsiUpdater) {
            val factory = KtPsiFactory(project)
            val condition = (if (replacementStrategy.negateCondition) negate(element.condition) else element.condition) ?: return
            val newExpression = replacementStrategy.operationText?.let { operationText ->
                val branchExpression = this@RemoveRedundantIf.branchExpressionPointer?.element?.let(updater::getWritable) ?: return
                factory.createExpressionByPattern(
                    "$0 $operationText $1",
                    condition.wrapForBooleanOperation(factory, operationText),
                    branchExpression.wrapForBooleanOperation(factory, operationText),
                )
            } ?: condition
            val newExpressionOnlyWithCondition = when (branchType) {
                is BranchType.Return -> factory.createExpressionByPattern("return $0", newExpression)
                is BranchType.LabeledReturn -> factory.createExpressionByPattern("return${branchType.label} $0", newExpression)
                is BranchType.Assign -> {
                    val lvalue = branchType.lvalue.element?.let(updater::getWritable) ?: return
                    factory.createExpressionByPattern("$0 = $1", lvalue, newExpression)
                }

                else -> newExpression
            }

            val comments = element.comments().map {
                // create a copy as all branches will be dropped
                val text = it.text
                if (it is PsiWhiteSpace) factory.createWhiteSpace(text) else factory.createComment(text)
            }

            /**
             * This is the case that we used the next expression of the if expression as the else expression.
             * See the code and comment in [redundancyInfo].
             */
            val returnExpressionAfterIf = returnExpressionAfterIfPointer?.element?.let(updater::getWritable)
            returnExpressionAfterIf?.let {
                it.parent.deleteChildRange(it.prevSibling as? PsiWhiteSpace ?: it, it)
            }

            val replaced = element.replace(newExpressionOnlyWithCondition)
            comments.reversed().forEach { replaced.parent.addAfter(it, replaced) }
        }

        private fun PsiElement.comments(): List<PsiElement> {
            val comments = LinkedHashSet<PsiElement>()
            accept(object : PsiRecursiveElementVisitor() {
                override fun visitComment(comment: PsiComment) {
                    (comment.prevSibling as? PsiWhiteSpace)?.let { comments.add(it) }
                    comments.add(comment)
                    (comment.nextSibling as? PsiWhiteSpace)?.let { comments.add(it) }
                }
            })
            return comments.toList().dropLastWhile { it is PsiWhiteSpace }
        }

        @RequiresBackgroundThread
        private fun negate(expression: KtExpression?): KtExpression? {
            if (expression == null) return null
            invertEmptinessCheck(expression)?.let { return it }
            return expression.negate(optionalBooleanExpressionCheck = ::isBooleanExpression)
        }

        private fun KtExpression.wrapForBooleanOperation(factory: KtPsiFactory, operationText: String): KtExpression {
            val operationPrecedence = PsiPrecedences.getPrecedence(factory.createExpression("a $operationText b"))
            return if (PsiPrecedences.getPrecedence(this) <= operationPrecedence) this
            else factory.createExpressionByPattern("($0)", this)
        }
    }
}

/**
 * Returns the sibling expression after [KtIfExpression] if it is [KtReturnExpression]. Otherwise, returns null.
 */
private fun KtIfExpression.returnAfterIf(): KtReturnExpression? = getNextSiblingIgnoringWhitespaceAndComments() as? KtReturnExpression