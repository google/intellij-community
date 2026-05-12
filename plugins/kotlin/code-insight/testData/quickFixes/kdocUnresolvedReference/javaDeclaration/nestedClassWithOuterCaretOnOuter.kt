// "Add link qualifier" "true"
// TOOL: org.jetbrains.kotlin.idea.k2.codeinsight.inspections.kdoc.KDocUnresolvedReferenceInspection

/**
 * [Collection<caret>s.UnmodifiableList]
 */
fun aaa(){}

// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.imprt.KDocUnresolvedLinkQuickFix
