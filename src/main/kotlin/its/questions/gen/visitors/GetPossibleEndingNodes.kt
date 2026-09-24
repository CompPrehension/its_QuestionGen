package its.questions.gen.visitors

import its.model.nodes.*
import its.model.nodes.visitors.SimpleDecisionTreeBehaviour
import its.questions.gen.QuestioningSituation
import its.reasoner.nodes.DecisionTreeReasoner.Companion.getAnswer
import its.reasoner.nodes.DecisionTreeReasoner.Companion.solve

class GetPossibleEndingNodes(
    val branch: ThoughtBranch,
    val situation: QuestioningSituation? = null,
) : SimpleDecisionTreeBehaviour<GetPossibleEndingNodes.PossibleEndingNodes> {

    private val correctTrace = situation?.forEval()?.let { branch.solve(it) }

    /**
     * Узел данной ветви, на котором фактически завершилось ее выполнение.
     *
     * Определяется как последний исполненный связующий узел, т.к. следующий за ним узел завершения
     * ([BranchResultNode] или [BranchResultRedirectingNode]) сам вопросов не порождает.
     * Узлы вызова процедур пропускаются - они не являются отдельным шагом рассуждения.
     */
    private val correctEndingNode = correctTrace
        ?.lastOrNull { it.node is LinkNode<*> && it.node !is ProcedureCallNode }
        ?.node

    data class PossibleEndingNodes(
        val endingNodes: Set<DecisionTreeNode> = setOf(),
        val correctEndingNode: DecisionTreeNode? = null,
    ) {
        operator fun plus(other: PossibleEndingNodes): PossibleEndingNodes {
            return PossibleEndingNodes(
                endingNodes + other.endingNodes, correctEndingNode ?: other.correctEndingNode
            )
        }
    }

    fun get(): PossibleEndingNodes {
        return process(branch)
    }

    // ---------------------- Функции поведения ---------------------------

    override fun <AnswerType : Any> process(node: LinkNode<AnswerType>): PossibleEndingNodes {
        val childrenRes = node.outcomes.map { it.node.use(this) }.fold(PossibleEndingNodes(), PossibleEndingNodes::plus)

        return childrenRes + getCurrentRes(node)
    }

    private fun <AnswerType : Any> getCurrentRes(node: LinkNode<AnswerType>): PossibleEndingNodes {
        return PossibleEndingNodes(
            if (endsWithMissingOutcome(node) || node.outcomes.any { it.node.isConclusion() }) setOf(node) else setOf(),
            if (node == correctEndingNode) node else null
        )
    }

    private fun DecisionTreeNode.isConclusion(): Boolean {
        return this is BranchResultNode || this is BranchResultRedirectingNode
    }

    private fun endsWithMissingOutcome(node: LinkNode<*>): Boolean {
        return (node is AggregationNode || node is WhileCycleNode)
               && node.possibleResults().any { !node.outcomes.containsKey(it) }
    }

    override fun process(node: FindActionNode): PossibleEndingNodes {
        if (situation == null) return process(node as LinkNode<*>)

        //Особое поведение, так как не интересуют конечные узлы, которые используют несуществующие переменные
        val childrenRes = node.outcomes[node.getAnswer(situation)]?.node?.use(this) ?: PossibleEndingNodes()

        return childrenRes + getCurrentRes(node)
    }

    override fun process(node: ProcedureCallNode): PossibleEndingNodes {
        //Вызов процедуры не является отдельным шагом рассуждения - узел прозрачно пропускается
        return node.outcomes[true]?.node?.use(this) ?: PossibleEndingNodes()
    }

    override fun process(node: BranchResultNode): PossibleEndingNodes {
        return PossibleEndingNodes()
    }

    override fun process(node: BranchResultRedirectingNode): PossibleEndingNodes {
        return PossibleEndingNodes()
    }

    override fun process(branch: ThoughtBranch): PossibleEndingNodes {
        return branch.start.use(this)
    }
}
