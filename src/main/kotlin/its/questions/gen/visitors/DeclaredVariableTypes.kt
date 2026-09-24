package its.questions.gen.visitors

import its.model.TypedVariable
import its.model.nodes.CycleAggregationNode
import its.model.nodes.DecisionTree
import its.model.nodes.DecisionTreeElement
import its.model.nodes.FindActionNode

internal fun DecisionTree.declaredVariableTypes(): Map<String, String> {
    val declared = variables + implicitVariables.map { it.variable } + collectDeclaredVariables(mainBranch)
    return declared.associate { it.varName to it.className }
}

private fun collectDeclaredVariables(element: DecisionTreeElement): List<TypedVariable> {
    val own = when (element) {
        is FindActionNode -> listOf(element.varAssignment.variable) + element.secondaryAssignments.map { it.variable }
        is CycleAggregationNode -> listOf(element.variable)
        else -> emptyList()
    }
    return own + element.linkedElements.flatMap { collectDeclaredVariables(it) }
}
