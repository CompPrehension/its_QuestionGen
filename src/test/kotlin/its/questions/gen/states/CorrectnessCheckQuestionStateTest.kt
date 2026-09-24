package its.questions.gen.states

import its.questions.gen.QuestionGenFixtures.domain
import its.questions.gen.QuestionGenFixtures.situation
import its.questions.gen.QuestioningSituation
import kotlin.test.Test
import kotlin.test.assertEquals

class CorrectnessCheckQuestionStateTest {

    private val vase = """
        class item [ RU.localizedName = "предмет" ; EN.localizedName = "item" ; ]
        obj a : item [ RU.localizedName = "ваза" ; EN.localizedName = "vase" ; ]
    """

    /** Верный вариант - «Верно.» без паузы, даже если у варианта есть своё объяснение. */
    @Test
    fun correctOptionIsConfirmed() {
        // Arrange.
        val state = object : CorrectnessCheckQuestionState<String>() {
            override fun text(situation: QuestioningSituation) = "Хрупкая ли ваза?"
            override fun options(situation: QuestioningSituation) = listOf(
                SingleChoiceOption("Да", Explanation("Ваза стеклянная.", ExplanationType.Error), Correctness("yes", true)),
                SingleChoiceOption("Нет", Explanation("Ваза стеклянная.", ExplanationType.Error), Correctness("no", false)),
            )
        }
        state.linkTo(EndQuestionState())

        // Act.
        val change = state.proceedWithAnswer(situation(domain(vase), "RU"), listOf(0))

        // Assert.
        assertEquals(Explanation("Верно.", ExplanationType.Success, shouldPause = false), change.explanation)
    }

    /** Неверный вариант объясняется объяснением этого варианта. */
    @Test
    fun incorrectOptionIsExplainedByItsExplanation() {
        // Arrange.
        val state = object : CorrectnessCheckQuestionState<String>() {
            override fun text(situation: QuestioningSituation) = "Хрупкая ли ваза?"
            override fun options(situation: QuestioningSituation) = listOf(
                SingleChoiceOption("Да", Explanation("Ваза стеклянная.", ExplanationType.Error), Correctness("yes", true)),
                SingleChoiceOption("Нет", Explanation("Ваза стеклянная.", ExplanationType.Error), Correctness("no", false)),
            )
        }
        state.linkTo(EndQuestionState())

        // Act.
        val change = state.proceedWithAnswer(situation(domain(vase), "RU"), listOf(1))

        // Assert.
        assertEquals(Explanation("Ваза стеклянная.", ExplanationType.Error), change.explanation)
    }

    /** Неверный вариант без объяснения - «Это неверно.» без паузы на языке ситуации. */
    @Test
    fun incorrectOptionWithoutExplanationGetsVerdict() {
        // Arrange.
        val state = object : CorrectnessCheckQuestionState<String>() {
            override fun text(situation: QuestioningSituation) = "Is the vase fragile?"
            override fun options(situation: QuestioningSituation) = listOf(
                SingleChoiceOption("Yes", null, Correctness("yes", true)),
                SingleChoiceOption("No", null, Correctness("no", false)),
            )
        }
        state.linkTo(EndQuestionState())

        // Act.
        val change = state.proceedWithAnswer(situation(domain(vase), "EN"), listOf(1))

        // Assert.
        assertEquals(Explanation("That's incorrect.", ExplanationType.Error, shouldPause = false), change.explanation)
    }

    /** Переходы получают правильность выбранного варианта вместе с ассоциированным ответом. */
    @Test
    fun linksSeeCorrectness() {
        // Arrange.
        val onCorrect = EndQuestionState()
        val onIncorrect = EndQuestionState()
        val state = object : CorrectnessCheckQuestionState<String>() {
            override fun text(situation: QuestioningSituation) = "Хрупкая ли ваза?"
            override fun options(situation: QuestioningSituation) = listOf(
                SingleChoiceOption("Да", null, Correctness("yes", true)),
                SingleChoiceOption("Нет", null, Correctness("no", false)),
            )
        }
        state.linkTo(onCorrect) { _, answer -> answer.isCorrect }
        state.linkTo(onIncorrect) { _, answer -> !answer.isCorrect && answer.answerInfo == "no" }

        // Act.
        val correct = state.proceedWithAnswer(situation(domain(vase), "RU"), listOf(0))
        val incorrect = state.proceedWithAnswer(situation(domain(vase), "RU"), listOf(1))

        // Assert.
        assertEquals(onCorrect, correct.nextState)
        assertEquals(onIncorrect, incorrect.nextState)
    }
}
