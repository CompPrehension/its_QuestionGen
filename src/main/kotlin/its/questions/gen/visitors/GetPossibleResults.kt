package its.questions.gen.visitors

import its.model.nodes.*
import its.model.nodes.visitors.SimpleDecisionTreeBehaviour

/**
 * TODO Class Description
 */
class GetPossibleResults : SimpleDecisionTreeBehaviour<Set<BranchResult>> {
    override fun <AnswerType : Any> process(node: LinkNode<AnswerType>): Set<BranchResult> {
        val possibleResults = node.children.flatMap { it.use(this) }.toMutableSet()
        if (node is AggregationNode) {
            possibleResults.addAll(BranchResult.entries.filter { !node.outcomes.containsKey(it) })
        }
        return possibleResults
    }

    override fun process(node: ProcedureCallNode): Set<BranchResult> {
        //Вызов процедуры не влияет на результат ветви - узел обрабатывается как обычный связующий
        return process(node as LinkNode<Boolean>)
    }

    override fun process(node: BranchResultNode): Set<BranchResult> {
        return setOf(node.value)
    }

    override fun process(node: BranchResultRedirectingNode): Set<BranchResult> {
        //Результат приходит из вызываемого графа и статически неизвестен - считаем возможными все результаты
        return BranchResult.entries.toSet()
    }

    override fun process(branch: ThoughtBranch): Set<BranchResult> {
        return branch.start.use(this)
    }

}