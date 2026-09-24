package its.questions.gen.states

import its.questions.gen.QuestionGenFixtures.domain
import its.questions.gen.QuestionGenFixtures.question
import its.questions.gen.QuestionGenFixtures.situation
import its.questions.gen.QuestioningSituation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class QuestionStateTest {

    private val vase = """
        class item [ RU.localizedName = "предмет" ; ]
        obj a : item [ RU.localizedName = "ваза" ; ]
    """

    private fun colorQuestion(): SingleChoiceQuestionState<String> = object : SingleChoiceQuestionState<String>() {
        override fun text(situation: QuestioningSituation) = "Какого цвета ваза?"
        override fun options(situation: QuestioningSituation) = listOf(SingleChoiceOption("Красная", null, "red"), SingleChoiceOption("Синяя", null, "blue"))
    }

    private fun skipTo(next: QuestionState): SkipQuestionState = object : SkipQuestionState() {
        override fun skip(situation: QuestioningSituation) = QuestionStateChange(Explanation("Пропуск."), next)
        override val reachableStates: Collection<QuestionState> = listOf(next)
    }

    /** Конечное состояние не задаёт вопросов, не даёт объяснений и никуда не ведёт. */
    @Test
    fun endStateFinishesDialog() {
        // Arrange.
        val end = EndQuestionState()
        val situation = situation(domain(vase), "RU")

        // Act & Assert.
        assertEquals(QuestionStateChange(null, null), end.getQuestion(situation))
        assertEquals(QuestionStateChange(null, null), end.proceedWithAnswer(situation, listOf(0)))
        assertEquals(emptyList(), end.reachableStates.toList())
    }

    /** Пропускающее состояние и при запросе вопроса, и при ответе выполняет пропуск. */
    @Test
    fun skipStateSkipsBothWays() {
        // Arrange.
        val next = EndQuestionState()
        val skip = skipTo(next)
        val situation = situation(domain(vase), "RU")

        // Act & Assert.
        assertEquals(QuestionStateChange(Explanation("Пропуск."), next), skip.getQuestion(situation))
        assertEquals(QuestionStateChange(Explanation("Пропуск."), next), skip.proceedWithAnswer(situation, listOf(0)))
    }

    /** Состояния с вопросами получают возрастающие положительные id, пропускающие - убывающие отрицательные. */
    @Test
    fun stateIdsAreUniqueBySign() {
        // Act.
        val firstQuestion = colorQuestion()
        val secondQuestion = colorQuestion()
        val firstSkip = EndQuestionState()
        val secondSkip = EndQuestionState()

        // Assert.
        assertTrue(firstQuestion.id > 0)
        assertEquals(firstQuestion.id + 1, secondQuestion.id)
        assertTrue(firstSkip.id < 0)
        assertEquals(firstSkip.id - 1, secondSkip.id)
    }

    /** Незавершённое перенаправление никуда не ведёт и имеет id 0. */
    @Test
    fun unfinalizedRedirect() {
        // Act.
        val redirect = RedirectQuestionState()

        // Assert.
        assertFalse(redirect.isFinalized())
        assertNull(redirect.redirectsTo())
        assertEquals(0, redirect.id)
        assertEquals(emptyList(), redirect.reachableStates.toList())
    }

    /** Перенаправление полностью делегирует целевому состоянию: id, вопрос, ответ и переходы. */
    @Test
    fun redirectDelegatesToTarget() {
        // Arrange.
        val next = EndQuestionState()
        val target = colorQuestion()
        target.linkTo(next)
        val redirect = RedirectQuestionState()
        val situation = situation(domain(vase), "RU")

        // Act.
        redirect.redir = target

        // Assert.
        assertTrue(redirect.isFinalized())
        assertSame(target, redirect.redirectsTo())
        assertEquals(target.id, redirect.id)
        assertEquals("Какого цвета ваза?", question(redirect.getQuestion(situation)).text)
        assertSame(next, redirect.proceedWithAnswer(situation, listOf(0)).nextState)
        assertEquals(listOf<QuestionState>(next), redirect.reachableStates.toList())
    }

    /** Перенаправление на завершённое перенаправление схлопывается до конечной цели. */
    @Test
    fun redirectChainCollapses() {
        // Arrange.
        val target = colorQuestion()
        val middle = RedirectQuestionState().apply { redir = target }
        val redirect = RedirectQuestionState()

        // Act.
        redirect.redir = middle

        // Assert.
        assertSame(target, redirect.redir)
        assertSame(target, redirect.redirectsTo())
    }

    /** Перенаправление на ещё не завершённое перенаправление ведёт к цели, заданной ему позже. */
    @Test
    fun redirectToLaterFinalizedRedirectFollowsIt() {
        // Arrange.
        val target = colorQuestion()
        val middle = RedirectQuestionState()
        val redirect = RedirectQuestionState()

        // Act.
        redirect.redir = middle
        middle.redir = target

        // Assert.
        assertSame(target, redirect.redirectsTo())
    }

    /** Обход достижимых состояний посещает каждое один раз, проходит сквозь перенаправления и не зацикливается. */
    @Test
    fun runForAllReachableVisitsEachStateOnce() {
        // Arrange.
        val question = colorQuestion()
        val redirectToQuestion = RedirectQuestionState().apply { redir = question }
        val end = EndQuestionState()
        val skip = skipTo(redirectToQuestion)
        question.linkTo(skip) { _, answer -> answer == "red" }
        question.linkTo(end) { _, answer -> answer == "blue" }
        val visited = mutableListOf<QuestionState>()

        // Act.
        skip.runForAllReachable { visited.add(this) }

        // Assert.
        assertEquals(listOf(skip, question, end), visited)
    }
}
