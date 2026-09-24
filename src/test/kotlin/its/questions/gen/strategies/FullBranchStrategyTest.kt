package its.questions.gen.strategies

import its.model.nodes.BranchResult
import its.questions.gen.Dialogs
import its.questions.gen.Dialogs.pick
import its.questions.gen.QuestionGenFixtures.domain
import its.questions.gen.QuestionGenFixtures.indexOf
import its.questions.gen.QuestionGenFixtures.optionTexts
import its.questions.gen.QuestionGenFixtures.question
import its.questions.gen.QuestionGenFixtures.situation
import its.questions.gen.QuestionGenFixtures.tree
import its.questions.gen.states.EndQuestionState
import its.questions.gen.states.Explanation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Полный разбор ветви [FullBranchStrategy]: выбор причины завершения, переход к общему предку и итог.
 */
class FullBranchStrategyTest {

    private val shop = """
        class item {
            obj prop weight: int [ RU.localizedName = "вес" ; EN.localizedName = "weight" ; ] ;
            obj prop price: int [ RU.localizedName = "цена" ; EN.localizedName = "price" ; ] ;
            obj prop fragile: bool [ RU.localizedName = "хрупкость" ; EN.localizedName = "fragility" ; ] ;
        } [ RU.localizedName = "предмет" ; EN.localizedName = "item" ; ]
        obj a : item { weight = 5 ; price = 10 ; fragile = true ; } [ RU.localizedName = "ваза" ; EN.localizedName = "vase" ; ]
        obj b : item { weight = 1 ; price = 10 ; fragile = false ; } [ RU.localizedName = "коробка" ; EN.localizedName = "box" ; ]
    """

    /** Без предполагаемого результата разбор начинается с вопроса о причине: варианты - причины завершения всех конечных узлов. */
    @Test
    fun startsWithEndingCauseChoice() {
        // Arrange.
        val tree = tree($$"""
            tpg Parcel(X: item) {
                ask (X.fragile) {
                    true -> { conclude: error };
                    false -> {
                        ask (X.weight > 3) {
                            true -> { conclude: error };
                            false -> { ask (X.price > 100) { true -> { conclude: error }; false -> { conclude: correct }; } as expensive; };
                        } as heavy;
                    };
                } as fragile;
            }
            [ alias = "parcel"; RU.description = "${X}[case='в'] ${$branchResult == BranchResult:CORRECT ? 'можно' : 'нельзя'} отправить"; ]
            meta for fragile [ alias = "fragile"; RU.endingCause = "Из-за хрупкости"; ]
            meta for heavy [ alias = "heavy"; RU.endingCause = "Из-за веса"; ]
            meta for expensive [ alias = "expensive"; RU.endingCause = "Из-за цены"; ]
        """)
        val automata = FullBranchStrategy.buildAndFinalize(tree.mainBranch, EndQuestionState())

        // Act.
        val question = question(automata.initState.getQuestion(situation(domain(shop), "RU", "X" to "b")))

        // Assert.
        assertEquals("Что из перечисленного применимо в данной ситуации?", question.text)
        assertEquals(listOf("Из-за цены", "Из-за веса", "Из-за хрупкости"), question.optionTexts())
    }

    /** С предполагаемым результатом ветви вопрос о причине спрашивает, почему студент так считает. */
    @Test
    fun assumedResultAsksWhyStudentThinksSo() {
        // Arrange.
        val tree = tree($$"""
            tpg Parcel(X: item) {
                ask (X.fragile) {
                    true -> { conclude: error };
                    false -> { ask (X.weight > 3) { true -> { conclude: error }; false -> { conclude: correct }; } as heavy; };
                } as fragile;
            }
            [ alias = "parcel"; RU.description = "${X}[case='в'] ${$branchResult == BranchResult:CORRECT ? 'можно' : 'нельзя'} отправить"; ]
            meta for fragile [ alias = "fragile"; RU.endingCause = "Из-за хрупкости"; ]
            meta for heavy [ alias = "heavy"; RU.endingCause = "Из-за веса"; ]
        """)
        val automata = FullBranchStrategy.buildAndFinalize(tree.mainBranch, EndQuestionState())
        val situation = situation(domain(shop), "RU", "X" to "a")
        situation.addAssumedResult(tree.mainBranch, BranchResult.CORRECT)

        // Act.
        val question = question(automata.initState.getQuestion(situation))

        // Assert.
        assertEquals("Почему вы считаете, что вазу можно отправить?", question.text)
    }

    /** Выбор причины переводит к вопросу общего предка выбранного и фактического конечных узлов с «Давайте разберемся.». */
    @Test
    fun chosenCauseLeadsToCommonAncestorQuestion() {
        // Arrange.
        val tree = tree($$"""
            tpg Parcel(X: item) {
                ask (X.fragile) {
                    true -> { conclude: error };
                    false -> {
                        ask (X.weight > 3) {
                            true -> { conclude: error };
                            false -> { ask (X.price > 100) { true -> { conclude: error }; false -> { conclude: correct }; } as expensive; };
                        } as heavy;
                    };
                } as fragile;
            }
            [ alias = "parcel"; RU.description = "${X}[case='в'] ${$branchResult == BranchResult:CORRECT ? 'можно' : 'нельзя'} отправить"; ]
            meta for fragile [ alias = "fragile"; RU.endingCause = "Из-за хрупкости"; ]
            meta for heavy [ alias = "heavy"; RU.endingCause = "Из-за веса"; ]
            meta for expensive [ alias = "expensive"; RU.endingCause = "Из-за цены"; ]
        """)
        val automata = FullBranchStrategy.buildAndFinalize(tree.mainBranch, EndQuestionState())
        val situation = situation(domain(shop), "RU", "X" to "b")
        val question = question(automata.initState.getQuestion(situation))

        // Act.
        val actual = automata.initState.proceedWithAnswer(situation, listOf(question.indexOf("Из-за цены")))
        val above = automata.initState.proceedWithAnswer(situation, listOf(question.indexOf("Из-за веса")))

        // Assert.
        assertEquals(Explanation("Давайте разберемся."), actual.explanation)
        assertEquals("Больше ли цена коробки 100?", question(actual.nextState!!.getQuestion(situation)).text)
        assertEquals(Explanation("Давайте разберемся."), above.explanation)
        assertEquals("Больше ли вес коробки 3?", question(above.nextState!!.getQuestion(situation)).text)
    }

    /** Если общий предок выше выбранного узла, разбор продолжается с вопроса о шаге, ведущем к этому предку. */
    @Test
    fun ancestorAboveChosenNodeStartsFromStepLeadingToIt() {
        // Arrange.
        val tree = tree($$"""
            tpg Parcel(X: item) {
                ask (X.price > 100) {
                    true -> { conclude: error };
                    false -> {
                        ask (X.fragile) {
                            true -> { ask (X.weight > 3) { true -> { conclude: error }; false -> { conclude: correct }; } as heavy; };
                            false -> { ask (X.weight > 10) { true -> { conclude: error }; false -> { conclude: correct }; } as veryHeavy; };
                        } as fragile;
                    };
                } as expensive;
            }
            [ alias = "parcel"; RU.description = "${X}[case='в'] ${$branchResult == BranchResult:CORRECT ? 'можно' : 'нельзя'} отправить"; ]
            meta for expensive [ alias = "expensive"; RU.endingCause = "Из-за цены"; ]
            meta for fragile [ alias = "fragile"; RU.asNextStep = "Проверить хрупкость."; ]
            meta for heavy [ alias = "heavy"; RU.asNextStep = "Проверить вес."; RU.endingCause = "Из-за веса хрупкого предмета"; ]
            meta for veryHeavy [ alias = "veryHeavy"; RU.asNextStep = "Проверить, очень ли тяжелый предмет."; RU.endingCause = "Из-за большого веса"; ]
        """)
        val automata = FullBranchStrategy.buildAndFinalize(tree.mainBranch, EndQuestionState())
        val situation = situation(domain(shop), "RU", "X" to "a")
        val question = question(automata.initState.getQuestion(situation))

        // Act.
        val change = automata.initState.proceedWithAnswer(situation, listOf(question.indexOf("Из-за большого веса")))

        // Assert.
        val nextQuestion = question(change.nextState!!.getQuestion(situation))
        assertEquals("Какой следующий шаг необходим для решения задачи?", nextQuestion.text)
        assertEquals(
            listOf(
                "Проверить хрупкость.",
                "Проверить вес.",
                "Проверить, очень ли тяжелый предмет.",
                "Можно заключить, что вазу можно отправить.",
                "Можно заключить, что вазу нельзя отправить.",
            ),
            nextQuestion.optionTexts(),
        )
    }

    /** Полный разбор: причина, вопросы узлов, шаги и итог «Итак, мы обсудили...» с фактическим результатом ветви. */
    @Test
    fun fullDialogTranscript() {
        // Arrange.
        val tree = tree($$"""
            tpg Parcel(X: item) {
                ask (X.fragile) {
                    true -> { conclude: error };
                    false -> {
                        ask (X.weight > 3) {
                            true -> { conclude: error };
                            false -> { ask (X.price > 100) { true -> { conclude: error }; false -> { conclude: correct }; } as expensive; };
                        } as heavy;
                    };
                } as fragile;
            }
            [ alias = "parcel"; RU.description = "${X}[case='в'] ${$branchResult == BranchResult:CORRECT ? 'можно' : 'нельзя'} отправить"; ]
            meta for fragile [ alias = "fragile"; RU.asNextStep = "Проверить хрупкость."; RU.endingCause = "Из-за хрупкости"; ]
            meta for heavy [ alias = "heavy"; RU.asNextStep = "Проверить вес."; RU.endingCause = "Из-за веса"; ]
            meta for expensive [ alias = "expensive"; RU.asNextStep = "Проверить цену."; RU.endingCause = "Из-за цены"; ]
        """)
        val automata = FullBranchStrategy.buildAndFinalize(tree.mainBranch, EndQuestionState())
        val situation = situation(domain(shop), "RU", "X" to "b")
        situation.addAssumedResult(tree.mainBranch, BranchResult.ERROR)

        // Act.
        val transcript = Dialogs.walk(
            automata.initState, situation,
            pick("Из-за веса"),
            pick("Да"),
            pick("Проверить цену."),
            pick("Нет"),
            pick("Можно заключить, что коробку можно отправить."),
        )

        // Assert.
        assertEquals(
            """
            ? Почему вы считаете, что коробку нельзя отправить?
              - Из-за цены
              - Из-за веса
              - Из-за хрупкости
            > Из-за веса
            ! Continue: Давайте разберемся.
            ? Больше ли вес коробки 3?
              - Да
              - Нет
            > Да
            ! Error: Это неверно, поскольку вес коробки имеет значение 1.
            ? Какой следующий шаг необходим для решения задачи?
              - Проверить цену.
              - Можно заключить, что коробку можно отправить.
              - Можно заключить, что коробку нельзя отправить.
            > Проверить цену.
            ! Success (auto): Верно.
            ? Больше ли цена коробки 100?
              - Да
              - Нет
            > Нет
            ! Success (auto): Верно.
            ? Какой следующий шаг необходим для решения задачи?
              - Можно заключить, что коробку можно отправить.
              - Можно заключить, что коробку нельзя отправить.
            > Можно заключить, что коробку можно отправить.
            ! Success (auto): Верно.
            ! Continue: Итак, мы обсудили, почему коробку можно отправить.
            """.trimIndent(),
            transcript.text,
        )
        assertEquals(BranchResult.CORRECT, situation.assumedResult(tree.mainBranch))
    }

    /** Английский разбор использует английские формулировки. */
    @Test
    fun englishDialogTranscript() {
        // Arrange.
        val tree = tree($$"""
            tpg Parcel(X: item) {
                ask (X.fragile) {
                    true -> { conclude: error };
                    false -> { ask (X.weight > 3) { true -> { conclude: error }; false -> { conclude: correct }; } as heavy; };
                } as fragile;
            }
            [ alias = "parcel"; EN.description = "${X} ${$branchResult == BranchResult:CORRECT ? 'can' : 'cannot'} be sent"; ]
            meta for fragile [ alias = "fragile"; EN.asNextStep = "Check fragility."; EN.endingCause = "Because of fragility"; ]
            meta for heavy [ alias = "heavy"; EN.asNextStep = "Check weight."; EN.endingCause = "Because of weight"; ]
        """)
        val automata = FullBranchStrategy.buildAndFinalize(tree.mainBranch, EndQuestionState())
        val situation = situation(domain(shop), "EN", "X" to "a")

        // Act.
        val transcript = Dialogs.walk(
            automata.initState, situation,
            pick("Because of fragility"),
            pick("No"),
            pick("We can conclude that vase cannot be sent."),
        )

        // Assert.
        assertEquals(
            """
            ? Which is true in this situation?
              - Because of weight
              - Because of fragility
            > Because of fragility
            ! Continue: Let's figure it out.
            ? Is fragility of vase equal to Yes?
              - Yes
              - No
            > No
            ! Error: That's incorrect, because fragility of vase is Yes.
            ? What is the next reasoning step in this case?
              - Check weight.
              - We can conclude that vase can be sent.
              - We can conclude that vase cannot be sent.
            > We can conclude that vase cannot be sent.
            ! Success (auto): Correct.
            ! Continue: So, we've discussed why vase cannot be sent.
            """.trimIndent(),
            transcript.text,
        )
    }

    /** При единственном конечном узле вопроса о причине нет, а разбор всё равно завершается итогом. */
    @Test
    fun singleEndingNodeSkipsCauseChoice() {
        // Arrange.
        val tree = tree($$"""
            tpg Parcel(X: item) {
                ask (X.price > 100) {
                    true -> { ask (X.fragile) { true -> { conclude: error }; false -> { conclude: correct }; } as fragileExpensive; };
                    false -> { ask (X.weight > 3) { true -> { conclude: error }; false -> { conclude: correct }; } as heavy; };
                } as expensive;
            }
            [ alias = "parcel"; RU.description = "${X}[case='в'] ${$branchResult == BranchResult:CORRECT ? 'можно' : 'нельзя'} отправить"; RU.nextStepExplanation = "Начинать нужно с цены."; ]
            meta for expensive [ alias = "expensive"; RU.asNextStep = "Проверить цену."; ]
            meta for fragileExpensive [ alias = "fragileExpensive"; RU.asNextStep = "Проверить хрупкость."; RU.endingCause = "Из-за хрупкости"; ]
            meta for heavy [ alias = "heavy"; RU.asNextStep = "Проверить вес."; RU.endingCause = "Из-за веса"; ]
        """)
        val singleEnding = tree($$"""
            tpg Parcel(X: item) {
                ask (X.price > 100) {
                    true -> out;
                    false -> out;
                } as expensive;
                ask (X.weight > 3) { true -> { conclude: error }; false -> { conclude: correct }; } as heavy;
            }
            [ alias = "parcel"; RU.description = "${X}[case='в'] ${$branchResult == BranchResult:CORRECT ? 'можно' : 'нельзя'} отправить"; RU.nextStepExplanation = "Начинать нужно с цены."; ]
            meta for expensive [ alias = "expensive"; RU.asNextStep = "Проверить цену."; ]
            meta for heavy [ alias = "heavy"; RU.asNextStep = "Проверить вес."; ]
        """)

        // Act.
        val twoEndings = FullBranchStrategy.buildAndFinalize(tree.mainBranch, EndQuestionState())
        val oneEnding = FullBranchStrategy.buildAndFinalize(singleEnding.mainBranch, EndQuestionState())
        val firstQuestion = question(twoEndings.initState.getQuestion(situation(domain(shop), "RU", "X" to "b")))
        val transcript = Dialogs.walk(oneEnding.initState, situation(domain(shop), "RU", "X" to "b")) { question -> listOf(question.options.first().second) }

        // Assert.
        assertEquals("Что из перечисленного применимо в данной ситуации?", firstQuestion.text)
        assertTrue(transcript.questions.none { it.text == "Что из перечисленного применимо в данной ситуации?" }, transcript.text)
        assertEquals("! Continue: Итак, мы обсудили, почему коробку можно отправить.", transcript.lines.last())
    }
}
