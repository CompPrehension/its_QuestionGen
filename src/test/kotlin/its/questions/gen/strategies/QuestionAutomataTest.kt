package its.questions.gen.strategies

import its.questions.gen.QuestioningSituation
import its.questions.gen.states.EndQuestionState
import its.questions.gen.states.QuestionState
import its.questions.gen.states.QuestionStateChange
import its.questions.gen.states.RedirectQuestionState
import its.questions.gen.states.SingleChoiceQuestionState
import its.questions.gen.states.SkipQuestionState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class QuestionAutomataTest {

    private fun colorQuestion(): SingleChoiceQuestionState<String> = object : SingleChoiceQuestionState<String>() {
        override fun text(situation: QuestioningSituation) = "Какого цвета ваза?"
        override fun options(situation: QuestioningSituation) = listOf(SingleChoiceOption("Красная", null, "red"), SingleChoiceOption("Синяя", null, "blue"))
    }

    private fun skipTo(next: QuestionState): SkipQuestionState = object : SkipQuestionState() {
        override fun skip(situation: QuestioningSituation) = QuestionStateChange(null, next)
        override val reachableStates: Collection<QuestionState> = listOf(next)
    }

    /** Автомат состоит из всех состояний, достижимых из начального; незавершённые перенаправления входят в него сами. */
    @Test
    fun automatonContainsReachableStates() {
        // Arrange.
        val question = colorQuestion()
        val openEnd = RedirectQuestionState()
        val end = EndQuestionState()
        question.linkTo(openEnd) { _, answer -> answer == "red" }
        question.linkTo(end) { _, answer -> answer == "blue" }
        val unreachable = EndQuestionState()

        // Act.
        val automata = QuestionAutomata(skipTo(question))

        // Assert.
        assertEquals(4, automata.size)
        assertTrue(automata.containsAll(listOf(question, openEnd, end)))
        assertFalse(unreachable in automata)
        assertSame(question, automata[question.id])
        assertFailsWith<NoSuchElementException> { automata[unreachable.id] }
    }

    /** Автомат «с вопросами», только если в нём есть состояние с вопросом (в том числе через перенаправление). */
    @Test
    fun hasQuestions() {
        // Arrange.
        val onlySkips = QuestionAutomata(skipTo(EndQuestionState()))
        val withQuestion = QuestionAutomata(skipTo(colorQuestion().apply { linkTo(EndQuestionState()) }))
        val redirectToQuestion = QuestionAutomata(RedirectQuestionState().apply { redir = colorQuestion() })

        // Act & Assert.
        assertFalse(onlySkips.hasQuestions())
        assertTrue(withQuestion.hasQuestions())
        assertTrue(redirectToQuestion.hasQuestions())
    }

    /** Завершение автомата направляет все его незавершённые концы в заданное состояние; повторно завершить нельзя. */
    @Test
    fun finalizeRedirectsOpenEnds() {
        // Arrange.
        val question = colorQuestion()
        val redEnd = RedirectQuestionState()
        val blueEnd = RedirectQuestionState()
        question.linkTo(redEnd) { _, answer -> answer == "red" }
        question.linkTo(blueEnd) { _, answer -> answer == "blue" }
        val automata = QuestionAutomata(question)
        val finish = EndQuestionState()
        val finalizedBefore = automata.isFinalized()

        // Act.
        automata.finalize(finish)

        // Assert.
        assertFalse(finalizedBefore)
        assertTrue(automata.isFinalized())
        assertSame(finish, redEnd.redirectsTo())
        assertSame(finish, blueEnd.redirectsTo())
        assertFailsWith<IllegalArgumentException> { automata.finalize(EndQuestionState()) }
    }

    /** Завершение через buildAndFinalize ведёт все концы ветви в заданное конечное состояние. */
    @Test
    fun buildAndFinalizeUsesEndState() {
        // Arrange.
        val question = colorQuestion()
        val openEnd = RedirectQuestionState()
        question.linkTo(openEnd)
        val strategy = object : QuestioningStrategy {
            override fun build(branch: its.model.nodes.ThoughtBranch) = QuestionAutomata(question)
        }
        val end = EndQuestionState()

        // Act.
        val automata = strategy.buildAndFinalize(its.model.nodes.ThoughtBranch(its.model.nodes.BranchResultNode(its.model.nodes.BranchResult.CORRECT, null)), end)

        // Assert.
        assertTrue(automata.isFinalized())
        assertSame(end, openEnd.redirectsTo())
        assertSame(FullBranchStrategy, QuestioningStrategy.defaultFullBranchStrategy)
    }
}
