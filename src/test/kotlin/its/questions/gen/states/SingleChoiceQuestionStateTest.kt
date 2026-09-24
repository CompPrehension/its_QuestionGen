package its.questions.gen.states

import its.questions.gen.QuestionGenFixtures.change
import its.questions.gen.QuestionGenFixtures.domain
import its.questions.gen.QuestionGenFixtures.optionTexts
import its.questions.gen.QuestionGenFixtures.question
import its.questions.gen.QuestionGenFixtures.situation
import its.questions.gen.QuestioningSituation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SingleChoiceQuestionStateTest {

    private val vase = """
        class item [ RU.localizedName = "предмет" ; ]
        obj a : item [ RU.localizedName = "ваза" ; ]
    """

    /** Вопрос с одиночным выбором показывает текст и варианты в заданном порядке. */
    @Test
    fun questionShowsTextAndOptionsInOrder() {
        // Arrange.
        val state = object : SingleChoiceQuestionState<String>() {
            override fun text(situation: QuestioningSituation) = "Какого цвета ваза?"
            override fun options(situation: QuestioningSituation) = listOf(
                SingleChoiceOption("Красная", null, "red"),
                SingleChoiceOption("Синяя", null, "blue"),
            )
        }

        // Act.
        val question = question(state.getQuestion(situation(domain(vase), "RU")))

        // Assert.
        assertEquals("Какого цвета ваза?", question.text)
        assertEquals(QuestionType.single, question.type)
        assertEquals(listOf("Красная" to 0, "Синяя" to 1), question.options)
        assertEquals(emptyList(), question.matchingOptions)
    }

    /** Ответ даёт объяснение выбранного варианта и переход по первой связи, условие которой выполнено. */
    @Test
    fun answerGivesOptionExplanationAndLinkedState() {
        // Arrange.
        val onRed = EndQuestionState()
        val onBlue = EndQuestionState()
        val state = object : SingleChoiceQuestionState<String>() {
            override fun text(situation: QuestioningSituation) = "Какого цвета ваза?"
            override fun options(situation: QuestioningSituation) = listOf(
                SingleChoiceOption("Красная", Explanation("Да, красная."), "red"),
                SingleChoiceOption("Синяя", Explanation("Нет, она красная.", ExplanationType.Error), "blue"),
            )
        }
        state.linkTo(onRed) { _, answer -> answer == "red" }
        state.linkTo(onBlue) { _, answer -> answer == "blue" }

        // Act.
        val change = state.proceedWithAnswer(situation(domain(vase), "RU"), listOf(1))

        // Assert.
        assertEquals(Explanation("Нет, она красная.", ExplanationType.Error), change.explanation)
        assertSame(onBlue, change.nextState)
    }

    /** При нескольких подходящих связях побеждает добавленная раньше; безусловная связь заменяет все прежние. */
    @Test
    fun firstMatchingLinkWinsAndUnconditionalLinkReplacesAll() {
        // Arrange.
        val first = EndQuestionState()
        val second = EndQuestionState()
        val only = EndQuestionState()
        val conditional = object : SingleChoiceQuestionState<String>() {
            override fun text(situation: QuestioningSituation) = "Какого цвета ваза?"
            override fun options(situation: QuestioningSituation) = listOf(SingleChoiceOption("Красная", null, "red"), SingleChoiceOption("Синяя", null, "blue"))
        }
        conditional.linkTo(first) { _, _ -> true }
        conditional.linkTo(second) { _, _ -> true }
        val unconditional = object : SingleChoiceQuestionState<String>() {
            override fun text(situation: QuestioningSituation) = "Какого цвета ваза?"
            override fun options(situation: QuestioningSituation) = listOf(SingleChoiceOption("Красная", null, "red"), SingleChoiceOption("Синяя", null, "blue"))
        }
        unconditional.linkTo(first) { _, _ -> true }
        unconditional.linkTo(only)

        // Act.
        val conditionalChange = conditional.proceedWithAnswer(situation(domain(vase), "RU"), listOf(0))
        val unconditionalChange = unconditional.proceedWithAnswer(situation(domain(vase), "RU"), listOf(0))

        // Assert.
        assertSame(first, conditionalChange.nextState)
        assertSame(only, unconditionalChange.nextState)
        assertEquals(listOf<QuestionState>(only), unconditional.reachableStates.toList())
    }

    /** Если ни одна связь не подходит к ответу - ошибка. */
    @Test
    fun answerWithoutMatchingLinkFails() {
        // Arrange.
        val state = object : SingleChoiceQuestionState<String>() {
            override fun text(situation: QuestioningSituation) = "Какого цвета ваза?"
            override fun options(situation: QuestioningSituation) = listOf(SingleChoiceOption("Красная", null, "red"), SingleChoiceOption("Синяя", null, "blue"))
        }
        state.linkTo(EndQuestionState()) { _, answer -> answer == "red" }

        // Act & Assert.
        assertFailsWith<NoSuchElementException> { state.proceedWithAnswer(situation(domain(vase), "RU"), listOf(1)) }
    }

    /** Ответ запоминается в ситуации по id состояния и доступен как ранее выбранный. */
    @Test
    fun answerIsRememberedInSituation() {
        // Arrange.
        val state = object : SingleChoiceQuestionState<String>() {
            override fun text(situation: QuestioningSituation) = "Какого цвета ваза?"
            override fun options(situation: QuestioningSituation) = listOf(SingleChoiceOption("Красная", null, "red"), SingleChoiceOption("Синяя", null, "blue"))
        }
        state.linkTo(EndQuestionState())
        val situation = situation(domain(vase), "RU")
        val before = state.previouslyChosenAnswer(situation)

        // Act.
        state.proceedWithAnswer(situation, listOf(1))

        // Assert.
        assertNull(before)
        assertEquals(mapOf(state.id to 1), situation.givenAnswers)
        assertEquals("blue", state.previouslyChosenAnswer(situation))
    }

    /** Дополнительные действия получают ассоциированный ответ до вычисления объяснения и перехода. */
    @Test
    fun additionalActionsRunBeforeExplanation() {
        // Arrange.
        val events = mutableListOf<String>()
        val state = object : SingleChoiceQuestionState<String>() {
            override fun text(situation: QuestioningSituation) = "Какого цвета ваза?"
            override fun options(situation: QuestioningSituation) = listOf(SingleChoiceOption("Красная", null, "red"), SingleChoiceOption("Синяя", null, "blue"))
            override fun additionalActions(situation: QuestioningSituation, chosenAnswer: String) { events.add("action $chosenAnswer") }
            override fun explanation(situation: QuestioningSituation, chosenOption: SingleChoiceOption<String>): Explanation? {
                events.add("explanation ${chosenOption.assocAnswer}")
                return null
            }
        }
        state.linkTo(EndQuestionState()) { _, _ -> events.add("link"); true }

        // Act.
        state.proceedWithAnswer(situation(domain(vase), "RU"), listOf(0))

        // Assert.
        assertEquals(listOf("action red", "explanation red", "link"), events)
    }

    /** Ответ должен состоять ровно из одного варианта. */
    @Test
    fun answerMustBeSingle() {
        // Arrange.
        val state = object : SingleChoiceQuestionState<String>() {
            override fun text(situation: QuestioningSituation) = "Какого цвета ваза?"
            override fun options(situation: QuestioningSituation) = listOf(SingleChoiceOption("Красная", null, "red"), SingleChoiceOption("Синяя", null, "blue"))
        }

        // Act.
        val error = assertFailsWith<IllegalArgumentException> { state.proceedWithAnswer(situation(domain(vase), "RU"), listOf(0, 1)) }

        // Assert.
        assertEquals("Invalid answer to a SingleChoiceQuestionState: [0, 1]", error.message)
    }

    /** Единственный вариант выбирается автоматически; объяснение варианта заменяется объяснением пропуска (по умолчанию его нет). */
    @Test
    fun singleOptionIsChosenAutomaticallyWithoutExplanation() {
        // Arrange.
        val next = EndQuestionState()
        val state = object : SingleChoiceQuestionState<String>() {
            override fun text(situation: QuestioningSituation) = "Какого цвета ваза?"
            override fun options(situation: QuestioningSituation) = listOf(SingleChoiceOption("Красная", Explanation("Да, красная."), "red"))
        }
        state.linkTo(next) { _, answer -> answer == "red" }
        val situation = situation(domain(vase), "RU")

        // Act.
        val change = change(state.getQuestion(situation))

        // Assert.
        assertNull(change.explanation)
        assertSame(next, change.nextState)
        assertEquals(mapOf(state.id to 0), situation.givenAnswers)
    }

    /** При автоматическом выборе единственного варианта показывается объяснение пропуска, если оно задано. */
    @Test
    fun singleOptionUsesSkipExplanation() {
        // Arrange.
        val state = object : SingleChoiceQuestionState<String>() {
            override fun text(situation: QuestioningSituation) = "Какого цвета ваза?"
            override fun options(situation: QuestioningSituation) = listOf(SingleChoiceOption("Красная", Explanation("Да, красная."), "red"))
            override fun explanationIfSkipped(situation: QuestioningSituation, skipOption: SingleChoiceOption<String>) =
                Explanation("Ваза может быть только ${skipOption.text.lowercase()}.", shouldPause = false)
        }
        state.linkTo(EndQuestionState())

        // Act.
        val change = change(state.getQuestion(situation(domain(vase), "RU")))

        // Assert.
        assertEquals(Explanation("Ваза может быть только красная.", shouldPause = false), change.explanation)
    }

    /** Предварительный пропуск возвращается как есть, варианты ответа при этом не строятся. */
    @Test
    fun preliminarySkipShortCircuits() {
        // Arrange.
        val skipTo = EndQuestionState()
        var optionsBuilt = false
        val state = object : SingleChoiceQuestionState<String>() {
            override fun text(situation: QuestioningSituation) = "Какого цвета ваза?"
            override fun options(situation: QuestioningSituation): List<SingleChoiceOption<String>> {
                optionsBuilt = true
                return listOf(SingleChoiceOption("Красная", null, "red"), SingleChoiceOption("Синяя", null, "blue"))
            }
            override fun preliminarySkip(situation: QuestioningSituation) = QuestionStateChange(Explanation("Уже обсуждали."), skipTo)
        }

        // Act.
        val change = change(state.getQuestion(situation(domain(vase), "RU")))

        // Assert.
        assertEquals(QuestionStateChange(Explanation("Уже обсуждали."), skipTo), change)
        assertEquals(false, optionsBuilt)
    }

    /** Флаг PREPEND_ID_TO_TEXT добавляет к тексту вопроса номер состояния. */
    @Test
    fun prependIdToText() {
        // Arrange.
        val state = object : SingleChoiceQuestionState<String>() {
            override fun text(situation: QuestioningSituation) = "Какого цвета ваза?"
            override fun options(situation: QuestioningSituation) = listOf(SingleChoiceOption("Красная", null, "red"), SingleChoiceOption("Синяя", null, "blue"))
        }
        val situation = situation(domain(vase), "RU")

        // Act.
        GeneralQuestionState.PREPEND_ID_TO_TEXT = true
        val text = try {
            question(state.getQuestion(situation)).text
        } finally {
            GeneralQuestionState.PREPEND_ID_TO_TEXT = false
        }

        // Assert.
        assertEquals("${state.id}. Какого цвета ваза?", text)
        assertTrue(state.id > 0)
    }

    /** Варианты вопроса не перемешиваются при построении; перемешивание разрешено только для показа в консоли. */
    @Test
    fun optionsAreNotShuffledOnBuild() {
        // Arrange.
        val state = object : SingleChoiceQuestionState<Int>() {
            override fun text(situation: QuestioningSituation) = "Сколько?"
            override fun options(situation: QuestioningSituation) = (1..20).map { SingleChoiceOption("$it", null, it) }
        }

        // Act.
        val question = question(state.getQuestion(situation(domain(vase), "RU")))

        // Assert.
        assertEquals((1..20).map { "$it" }, question.optionTexts())
        assertTrue(question.shouldShuffle)
    }
}
