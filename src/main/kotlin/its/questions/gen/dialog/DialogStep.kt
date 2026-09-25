package its.questions.gen.dialog

import its.questions.gen.states.Explanation
import its.questions.gen.states.Question
import its.questions.gen.states.QuestionState

data class DialogStep(
    val explanations: List<Explanation>,
    val question: Question?,
    val state: QuestionState?,
) {
    val isFinished: Boolean
        get() = question == null
}
