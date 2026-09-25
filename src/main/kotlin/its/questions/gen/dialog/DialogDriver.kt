package its.questions.gen.dialog

import its.questions.gen.QuestioningSituation
import its.questions.gen.states.Explanation
import its.questions.gen.states.Question
import its.questions.gen.states.QuestionState
import its.questions.gen.states.QuestionStateChange
import its.questions.gen.states.RedirectQuestionState
import its.questions.gen.strategies.QuestionAutomata

object DialogDriver {

    const val MAX_TRANSITIONS = 1000

    @JvmStatic
    fun start(automata: QuestionAutomata, situation: QuestioningSituation): DialogStep {
        return resume(automata.initState, situation)
    }

    @JvmStatic
    fun resume(state: QuestionState?, situation: QuestioningSituation): DialogStep {
        return advance(state, situation, mutableListOf())
    }

    @JvmStatic
    fun answer(state: QuestionState, situation: QuestioningSituation, answer: List<Int>): DialogStep {
        val change = state.proceedWithAnswer(situation, answer)
        return advance(change, situation)
    }

    private fun advance(change: QuestionStateChange, situation: QuestioningSituation): DialogStep {
        val explanations = mutableListOf<Explanation>()
        change.explanation?.let { explanations.append(it, situation) }
        return advance(change.nextState, situation, explanations)
    }

    private fun MutableList<Explanation>.append(explanation: Explanation, situation: QuestioningSituation) {
        val previous = lastOrNull()
        if (previous == null || previous.discussedResults.isEmpty() || explanation.discussedResults.isEmpty()) {
            add(explanation)
            return
        }
        val results = previous.discussedResults + explanation.discussedResults
        set(lastIndex, Explanation(
            situation.localization.SO_WEVE_DISCUSSED_WHY_ALL(results),
            shouldPause = previous.shouldPause || explanation.shouldPause,
            discussedResults = results,
        ))
    }

    private fun advance(
        from: QuestionState?,
        situation: QuestioningSituation,
        explanations: MutableList<Explanation>,
    ): DialogStep {
        var state = from
        repeat(MAX_TRANSITIONS) {
            val current = state ?: return DialogStep(explanations, null, null)
            when (val result = current.getQuestion(situation)) {
                is Question -> return DialogStep(explanations, result, current.resolved())
                is QuestionStateChange -> {
                    result.explanation?.let { explanations.append(it, situation) }
                    state = result.nextState
                }
            }
        }
        throw IllegalStateException("Dialog made no progress to a question or an end in $MAX_TRANSITIONS transitions")
    }

    private fun QuestionState.resolved(): QuestionState {
        return if (this is RedirectQuestionState) redirectsTo() ?: this else this
    }
}
