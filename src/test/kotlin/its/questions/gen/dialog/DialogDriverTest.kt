package its.questions.gen.dialog

import its.model.nodes.BranchResult
import its.questions.gen.Dialogs
import its.questions.gen.Dialogs.pick
import its.questions.gen.Dialogs.pickAll
import its.questions.gen.Dialogs.match
import its.questions.gen.QuestionGenFixtures.domain
import its.questions.gen.QuestionGenFixtures.situation
import its.questions.gen.QuestionGenFixtures.tree
import its.questions.gen.QuestioningSituation
import its.questions.gen.states.EndQuestionState
import its.questions.gen.states.Explanation
import its.questions.gen.states.ExplanationType
import its.questions.gen.states.QuestionState
import its.questions.gen.states.QuestionStateChange
import its.questions.gen.states.RedirectQuestionState
import its.questions.gen.states.SingleChoiceQuestionState
import its.questions.gen.states.SkipQuestionState
import its.questions.gen.strategies.FullBranchStrategy
import its.questions.gen.strategies.QuestionAutomata
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Шаги диалога [DialogDriver]: пропуски проходятся внутри шага, объяснения копятся до ближайшего вопроса или конца.
 */
class DialogDriverTest {

    private val vase = """
        class item [ RU.localizedName = "предмет" ; ]
        obj a : item [ RU.localizedName = "ваза" ; ]
    """

    private val packing = """
        class box {
            obj prop weight: int [ RU.localizedName = "вес" ; ] ;
        } [ RU.localizedName = "коробка" ; ]
        class item {
            obj prop fragile: bool [ RU.localizedName = "хрупкость" ; ] ;
            obj prop wrapped: bool [ RU.localizedName = "упакованность" ; ] ;
            rel inside(box) ;
        } [ RU.localizedName = "предмет" ; ]
        obj box1 : box { weight = 3 ; } [ RU.localizedName = "коробка" ; ]
        obj cup : item { fragile = true ; wrapped = false ; inside(box1) ; } [ RU.localizedName = "чашка" ; ]
        obj book : item { fragile = false ; wrapped = false ; inside(box1) ; } [ RU.localizedName = "книга" ; ]
    """

    /** Отправка коробки: тело цикла по её предметам - вложенная ветвь, которая при разборе закрывается последней. */
    private val shipping = $$"""
        tpg Packing(X: box) {
            cycle and ($i=>inside(X)) with item i {
                _ -[body]-> {
                    ask (i.fragile) {
                        true -> { ask (i.wrapped) { true -> { conclude: correct }; false -> { conclude: error }; } as iWrapped; };
                        false -> { conclude: correct };
                    } as iFragile;
                };
                correct -> { conclude: correct };
                error -> { conclude: error };
                null -> { conclude: correct };
            } as contents;
        }
        [ alias = "packing"; RU.description = "${X}[case='в'] ${$branchResult == BranchResult:CORRECT ? 'можно' : 'нельзя'} отправить"; ]
        meta for contents [ alias = "contents"; RU.question = "Какие предметы лежат в ${X}[case='п']?"; ]
        meta for body [ alias = "body"; RU.description = "${i}[case='и'] ${$branchResult == BranchResult:CORRECT ? 'не мешает' : 'мешает'} отправке"; ]
        meta for iFragile [ alias = "iFragile"; RU.question = "Хрупкий ли ${i}?"; RU.asNextStep = "Проверить хрупкость."; RU.endingCause = "Из-за хрупкости"; ]
        meta for iWrapped [ alias = "iWrapped"; RU.question = "Упакован ли ${i}?"; RU.asNextStep = "Проверить упаковку."; RU.endingCause = "Из-за упаковки"; ]
    """

    private fun colorQuestion(vararg colors: String): SingleChoiceQuestionState<String> = object : SingleChoiceQuestionState<String>() {
        override fun text(situation: QuestioningSituation) = "Какого цвета ваза?"
        override fun options(situation: QuestioningSituation) = colors.map { SingleChoiceOption(it, Explanation("Выбран $it.", shouldPause = false), it) }
        override fun explanationIfSkipped(situation: QuestioningSituation, skipOption: SingleChoiceOption<String>) = Explanation("Ваза может быть только ${skipOption.text}.")
    }

    private fun summary(result: String, pause: Boolean = true) =
        Explanation("Итак, мы обсудили, почему $result.", shouldPause = pause, discussedResults = listOf(result))

    private fun skipTo(next: QuestionState, explanation: Explanation?): SkipQuestionState = object : SkipQuestionState() {
        override fun skip(situation: QuestioningSituation) = QuestionStateChange(explanation, next)
        override val reachableStates: Collection<QuestionState> = listOf(next)
    }

    /** Объяснения всех пропусков перед вопросом приходят вместе с вопросом, в порядке выдачи. */
    @Test
    fun explanationsBeforeQuestionComeWithIt() {
        // Arrange.
        val question = colorQuestion("красная", "синяя").apply { linkTo(EndQuestionState()) }
        val start = skipTo(skipTo(skipTo(question, Explanation("Второе.", shouldPause = false)), null), Explanation("Первое."))

        // Act.
        val step = DialogDriver.resume(start, situation(domain(vase), "RU"))

        // Assert.
        assertEquals(listOf(Explanation("Первое."), Explanation("Второе.", shouldPause = false)), step.explanations)
        assertEquals("Какого цвета ваза?", step.question!!.text)
        assertSame(question, step.state)
        assertFalse(step.isFinished)
    }

    /** Ответ, после которого остаются только пропуски, завершает диалог одним шагом со всеми объяснениями. */
    @Test
    fun answerFollowedOnlyBySkipsFinishesDialog() {
        // Arrange.
        val question = colorQuestion("красная", "синяя")
        question.linkTo(skipTo(skipTo(skipTo(EndQuestionState(), Explanation("Итог внешний.")), null), Explanation("Итог внутренний.")))
        val situation = situation(domain(vase), "RU")

        // Act.
        val step = DialogDriver.answer(question, situation, listOf(0))

        // Assert.
        assertEquals(
            listOf(Explanation("Выбран красная.", shouldPause = false), Explanation("Итог внутренний."), Explanation("Итог внешний.")),
            step.explanations,
        )
        assertTrue(step.isFinished)
        assertNull(step.question)
        assertNull(step.state)
    }

    /** Итоги вложенной ветви и главной ветви, идущие подряд после последнего ответа, сворачиваются в один итог завершающего шага. */
    @Test
    fun nestedBranchClosedLastFinishesInOneStep() {
        // Arrange.
        val tree = tree(shipping)
        val automata = FullBranchStrategy.buildAndFinalize(tree.mainBranch, EndQuestionState())
        val situation = situation(domain(packing), "RU", "X" to "box1")
        situation.addAssumedResult(tree.mainBranch, BranchResult.CORRECT)
        val script = mutableListOf(
            pickAll("чашка", "книга"),
            match("Верно", "Верно"),
            pick("Из-за хрупкости"),
            pick("Нет"),
            pick("Можно заключить, что чашка мешает отправке."),
            pick("Нет"),
        )
        var step = DialogDriver.start(automata, situation)
        while (script.isNotEmpty()) {
            step = DialogDriver.answer(step.state!!, situation, Dialogs.resolve(step.question!!, script.removeAt(0)))
        }
        val lastQuestion = step.question!!

        // Act.
        val last = DialogDriver.answer(step.state!!, situation, Dialogs.resolve(lastQuestion, pick("Можно заключить, что чашка мешает отправке.")))

        // Assert.
        assertEquals("Какой следующий шаг необходим для решения задачи?", lastQuestion.text)
        assertEquals(
            listOf(
                Explanation("Верно.", ExplanationType.Success, shouldPause = false),
                Explanation(
                    "Итак, мы обсудили, почему чашка мешает отправке и почему коробку нельзя отправить.",
                    discussedResults = listOf("чашка мешает отправке", "коробку нельзя отправить"),
                ),
            ),
            last.explanations,
        )
        assertTrue(last.isFinished)
    }

    /** Несколько итогов подряд становятся одной фразой; пауза нужна, если её требовал хоть один из них. */
    @Test
    fun summariesInARowAreMergedIntoOne() {
        // Arrange.
        val end = EndQuestionState()
        val start = skipTo(skipTo(skipTo(end, summary("ваза упадёт", pause = false)), summary("полка кривая", pause = true)), summary("гвоздь слабый", pause = false))

        // Act.
        val step = DialogDriver.resume(start, situation(domain(vase), "RU"))

        // Assert.
        assertEquals(
            listOf(Explanation(
                "Итак, мы обсудили, почему гвоздь слабый, почему полка кривая и почему ваза упадёт.",
                shouldPause = true,
                discussedResults = listOf("гвоздь слабый", "полка кривая", "ваза упадёт"),
            )),
            step.explanations,
        )
    }

    /** Итоги, между которыми есть другое объяснение, не сворачиваются. */
    @Test
    fun summariesSeparatedByOtherExplanationStayApart() {
        // Arrange.
        val end = EndQuestionState()
        val start = skipTo(skipTo(skipTo(end, summary("ваза упадёт")), Explanation("Верно.")), summary("полка кривая"))

        // Act.
        val step = DialogDriver.resume(start, situation(domain(vase), "RU"))

        // Assert.
        assertEquals(listOf(summary("полка кривая"), Explanation("Верно."), summary("ваза упадёт")), step.explanations)
    }

    /** Вопрос с единственным вариантом пропускается внутри шага, его объяснение попадает в шаг. */
    @Test
    fun singleOptionQuestionIsPassedWithinStep() {
        // Arrange.
        val next = colorQuestion("красная", "синяя").apply { linkTo(EndQuestionState()) }
        val single = colorQuestion("красная").apply { linkTo(next) }

        // Act.
        val step = DialogDriver.resume(single, situation(domain(vase), "RU"))

        // Assert.
        assertEquals(listOf(Explanation("Ваза может быть только красная.")), step.explanations)
        assertSame(next, step.state)
    }

    /** Шаг, пришедший к вопросу через перенаправление, указывает на само состояние вопроса. */
    @Test
    fun redirectToQuestionIsResolved() {
        // Arrange.
        val question = colorQuestion("красная", "синяя").apply { linkTo(EndQuestionState()) }
        val redirect = RedirectQuestionState().apply { redir = question }

        // Act.
        val step = DialogDriver.start(QuestionAutomata(redirect), situation(domain(vase), "RU"))

        // Assert.
        assertSame(question, step.state)
    }

    /** Возобновление с состояния вопроса повторяет тот же вопрос и не меняет ситуацию. */
    @Test
    fun resumeOnQuestionIsRepeatable() {
        // Arrange.
        val question = colorQuestion("красная", "синяя").apply { linkTo(EndQuestionState()) }
        val situation = situation(domain(vase), "RU")
        val first = DialogDriver.resume(question, situation)

        // Act.
        val second = DialogDriver.resume(first.state, situation)

        // Assert.
        assertEquals(first.question!!.text, second.question!!.text)
        assertEquals(emptyList(), second.explanations)
        assertEquals(emptyMap(), situation.givenAnswers)
    }

    /** Возобновление без состояния - законченный диалог без объяснений. */
    @Test
    fun resumeWithoutStateIsFinished() {
        // Act.
        val step = DialogDriver.resume(null, situation(domain(vase), "RU"))

        // Assert.
        assertEquals(DialogStep(emptyList(), null, null), step)
        assertTrue(step.isFinished)
    }

    /** Цикл из одних пропусков не зависает, а завершается ошибкой. */
    @Test
    fun skipCycleFails() {
        // Arrange.
        val loop = object : SkipQuestionState() {
            override fun skip(situation: QuestioningSituation) = QuestionStateChange(null, this)
            override val reachableStates: Collection<QuestionState> get() = listOf(this)
        }

        // Act.
        val error = assertFailsWith<IllegalStateException> { DialogDriver.resume(loop, situation(domain(vase), "RU")) }

        // Assert.
        assertEquals("Dialog made no progress to a question or an end in ${DialogDriver.MAX_TRANSITIONS} transitions", error.message)
    }
}
