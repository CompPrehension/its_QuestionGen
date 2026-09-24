package its.questions.gen.states

import its.questions.gen.QuestionGenFixtures.change
import its.questions.gen.QuestionGenFixtures.domain
import its.questions.gen.QuestionGenFixtures.question
import its.questions.gen.QuestionGenFixtures.situation
import its.questions.gen.QuestioningSituation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class MultipleChoiceQuestionStateTest {

    private val vase = """
        class item [ RU.localizedName = "предмет" ; EN.localizedName = "item" ; ]
        obj a : item [ RU.localizedName = "ваза" ; EN.localizedName = "vase" ; ]
    """

    /** Вопрос с множественным выбором показывает текст и варианты в заданном порядке. */
    @Test
    fun questionShowsTextAndOptions() {
        // Arrange.
        val state = object : MultipleChoiceQuestionState<String>() {
            override fun text(situation: QuestioningSituation) = "Какие предметы хрупкие?"
            override fun options(situation: QuestioningSituation) = listOf(
                MultipleChoiceOption("Ваза", "vase", true, "Ваза тоже хрупкая."),
                MultipleChoiceOption("Мяч", "ball", false, "Мяч не хрупкий."),
            )
        }

        // Act.
        val question = question(state.getQuestion(situation(domain(vase), "RU")))

        // Assert.
        assertEquals("Какие предметы хрупкие?", question.text)
        assertEquals(QuestionType.multiple, question.type)
        assertEquals(listOf("Ваза" to 0, "Мяч" to 1), question.options)
    }

    /** Выбраны ровно правильные варианты - «Верно.» без паузы. */
    @Test
    fun exactlyCorrectChoiceIsAccepted() {
        // Arrange.
        val state = object : MultipleChoiceQuestionState<String>() {
            override fun text(situation: QuestioningSituation) = "Какие предметы хрупкие?"
            override fun options(situation: QuestioningSituation) = listOf(
                MultipleChoiceOption("Ваза", "vase", true, "Ваза тоже хрупкая."),
                MultipleChoiceOption("Мяч", "ball", false, "Мяч не хрупкий."),
                MultipleChoiceOption("Стакан", "glass", true, "Стакан тоже хрупкий."),
            )
        }
        state.linkTo(EndQuestionState())

        // Act.
        val change = state.proceedWithAnswer(situation(domain(vase), "RU"), listOf(2, 0))

        // Assert.
        assertEquals(Explanation("Верно.", ExplanationType.Success, shouldPause = false), change.explanation)
    }

    /** Ошибка: «Это неверно.», затем по строке объяснения каждого пропущенного, а после - каждого лишнего варианта. */
    @Test
    fun mistakesAreExplainedLineByLine() {
        // Arrange.
        val state = object : MultipleChoiceQuestionState<String>() {
            override fun text(situation: QuestioningSituation) = "Какие предметы хрупкие?"
            override fun options(situation: QuestioningSituation) = listOf(
                MultipleChoiceOption("Мяч", "ball", false, "Мяч не хрупкий."),
                MultipleChoiceOption("Ваза", "vase", true, "Ваза тоже хрупкая."),
                MultipleChoiceOption("Кубик", "cube", false, "Кубик не хрупкий."),
                MultipleChoiceOption("Стакан", "glass", true, "Стакан тоже хрупкий."),
            )
        }
        state.linkTo(EndQuestionState())

        // Act.
        val change = state.proceedWithAnswer(situation(domain(vase), "RU"), listOf(0, 2, 3))

        // Assert.
        assertEquals(
            Explanation("Это неверно.\nВаза тоже хрупкая.\nМяч не хрупкий.\nКубик не хрупкий.", ExplanationType.Error),
            change.explanation,
        )
    }

    /** Реплика об ошибке берётся из локализации ситуации. */
    @Test
    fun mistakeVerdictIsLocalized() {
        // Arrange.
        val state = object : MultipleChoiceQuestionState<String>() {
            override fun text(situation: QuestioningSituation) = "Which items are fragile?"
            override fun options(situation: QuestioningSituation) = listOf(MultipleChoiceOption("Vase", "vase", true, "Vase is fragile too."))
        }
        state.linkTo(EndQuestionState())

        // Act.
        val change = state.proceedWithAnswer(situation(domain(vase), "EN"), emptyList())

        // Assert.
        assertEquals(Explanation("That's incorrect.\nVase is fragile too.", ExplanationType.Error), change.explanation)
    }

    /** Когда правильных вариантов нет, пустой выбор верен. */
    @Test
    fun emptyChoiceIsCorrectWhenNothingFits() {
        // Arrange.
        val state = object : MultipleChoiceQuestionState<String>() {
            override fun text(situation: QuestioningSituation) = "Какие предметы хрупкие?"
            override fun options(situation: QuestioningSituation) = listOf(MultipleChoiceOption("Мяч", "ball", false, "Мяч не хрупкий."))
        }
        state.linkTo(EndQuestionState())

        // Act.
        val change = state.proceedWithAnswer(situation(domain(vase), "RU"), emptyList())

        // Assert.
        assertEquals(ExplanationType.Success, change.explanation!!.type)
    }

    /** Переход выбирается по списку правильных ответов, а не по выбранным; дополнительные действия получают выбранные. */
    @Test
    fun transitionUsesCorrectAnswersActionsUseChosen() {
        // Arrange.
        val seenByLinks = mutableListOf<List<String>>()
        val seenByActions = mutableListOf<List<String>>()
        val state = object : MultipleChoiceQuestionState<String>() {
            override fun text(situation: QuestioningSituation) = "Какие предметы хрупкие?"
            override fun options(situation: QuestioningSituation) = listOf(
                MultipleChoiceOption("Ваза", "vase", true, "Ваза тоже хрупкая."),
                MultipleChoiceOption("Мяч", "ball", false, "Мяч не хрупкий."),
            )
            override fun additionalActions(situation: QuestioningSituation, chosenAnswers: List<String>) { seenByActions.add(chosenAnswers) }
        }
        state.linkTo(EndQuestionState()) { _, answers -> seenByLinks.add(answers); true }

        // Act.
        state.proceedWithAnswer(situation(domain(vase), "RU"), listOf(1))

        // Assert.
        assertEquals(listOf(listOf("ball")), seenByActions)
        assertEquals(listOf(listOf("vase")), seenByLinks)
    }

    /** По умолчанию вопрос с единственным вариантом всё равно задаётся. */
    @Test
    fun singleOptionIsAskedByDefault() {
        // Arrange.
        val state = object : MultipleChoiceQuestionState<String>() {
            override fun text(situation: QuestioningSituation) = "Какие предметы хрупкие?"
            override fun options(situation: QuestioningSituation) = listOf(MultipleChoiceOption("Ваза", "vase", true, "Ваза тоже хрупкая."))
        }

        // Act.
        val question = question(state.getQuestion(situation(domain(vase), "RU")))

        // Assert.
        assertEquals(listOf("Ваза" to 0), question.options)
    }

    /** С флагом пропуска единственный вариант выбирается автоматически с объяснением пропуска вместо оценки. */
    @Test
    fun singleOptionIsSkippedWhenAllowed() {
        // Arrange.
        val next = EndQuestionState()
        val state = object : MultipleChoiceQuestionState<String>() {
            override val shouldSkipIfASingleOption = true
            override fun text(situation: QuestioningSituation) = "Какие предметы хрупкие?"
            override fun options(situation: QuestioningSituation) = listOf(MultipleChoiceOption("Ваза", "vase", true, "Ваза тоже хрупкая."))
        }
        state.linkTo(next)

        // Act.
        val change = change(state.getQuestion(situation(domain(vase), "RU")))

        // Assert.
        assertNull(change.explanation)
        assertSame(next, change.nextState)
    }

    /** Предварительный пропуск возвращается как есть. */
    @Test
    fun preliminarySkipIsReturned() {
        // Arrange.
        val skipTo = EndQuestionState()
        val state = object : MultipleChoiceQuestionState<String>() {
            override fun text(situation: QuestioningSituation) = "Какие предметы хрупкие?"
            override fun options(situation: QuestioningSituation) = emptyList<MultipleChoiceOption<String>>()
            override fun preliminarySkip(situation: QuestioningSituation) = QuestionStateChange(null, skipTo)
        }

        // Act.
        val change = change(state.getQuestion(situation(domain(vase), "RU")))

        // Assert.
        assertEquals(QuestionStateChange(null, skipTo), change)
    }
}
