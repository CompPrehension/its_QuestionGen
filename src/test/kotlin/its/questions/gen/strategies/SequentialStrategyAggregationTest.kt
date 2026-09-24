package its.questions.gen.strategies

import its.model.nodes.BranchAggregationNode
import its.model.nodes.BranchResult
import its.questions.gen.Dialogs
import its.questions.gen.Dialogs.match
import its.questions.gen.Dialogs.pick
import its.questions.gen.QuestionGenFixtures.change
import its.questions.gen.QuestionGenFixtures.domain
import its.questions.gen.QuestionGenFixtures.element
import its.questions.gen.QuestionGenFixtures.indexOf
import its.questions.gen.QuestionGenFixtures.optionTexts
import its.questions.gen.QuestionGenFixtures.question
import its.questions.gen.QuestionGenFixtures.situation
import its.questions.gen.QuestionGenFixtures.tree
import its.questions.gen.states.EndQuestionState
import its.questions.gen.states.Explanation
import its.questions.gen.states.ExplanationType
import its.questions.gen.states.RedirectQuestionState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Вопросы [SequentialStrategy] к узлам агрегации: итог AND/OR после сопоставления, MUTEX, HYP.
 */
class SequentialStrategyAggregationTest {

    private val shop = """
        class item {
            obj prop weight: int [ RU.localizedName = "вес" ; ] ;
            obj prop price: int [ RU.localizedName = "цена" ; ] ;
            obj prop fragile: bool [ RU.localizedName = "хрупкость" ; ] ;
        } [ RU.localizedName = "предмет" ; ]
        obj a : item { weight = 5 ; price = 10 ; fragile = true ; } [ RU.localizedName = "ваза" ; ]
        obj b : item { weight = 1 ; price = 10 ; fragile = false ; } [ RU.localizedName = "коробка" ; ]
        obj c : item { weight = 1 ; price = 10 ; fragile = true ; } [ RU.localizedName = "чашка" ; ]
    """

    /** После сопоставления спрашивается итог узла; неверный итог объясняется правилом агрегации без паузы. */
    @Test
    fun nodeResultQuestionAfterMatching() {
        // Arrange.
        val tree = tree($$"""
            tpg Parcel(X: item) {
                agg and {
                    _ -[light]-> { ask (X.weight > 3) { true -> { conclude: error }; false -> { conclude: correct }; } as heavy; };
                    _ -[sturdy]-> { ask (X.fragile) { true -> { conclude: error }; false -> { conclude: correct }; } as fragile; };
                    correct -> { ask (X.price > 100) { true -> { conclude: error }; false -> { conclude: correct }; } as expensive; };
                    error -> { conclude: error };
                } as sending;
            }
            [ alias = "parcel"; RU.description = "посылку можно отправить"; ]
            meta for sending [ alias = "sending"; RU.description = "${X}[case='в'] ${$branchResult == BranchResult:CORRECT ? 'можно' : 'нельзя'} отправить без упаковки"; ]
            meta for light [ alias = "light"; RU.description = "${X}[case='и'] ${$branchResult == BranchResult:CORRECT ? 'легкая' : 'тяжелая'}"; ]
            meta for sturdy [ alias = "sturdy"; RU.description = "${X}[case='и'] ${$branchResult == BranchResult:CORRECT ? 'прочная' : 'хрупкая'}"; ]
            meta for expensive [ alias = "expensive"; RU.asNextStep = "Проверить цену."; ]
        """)
        val matching = SequentialStrategy.buildWithInfo(tree.mainBranch).info.nodeStates[tree.element<BranchAggregationNode>("sending")]!!
        val situation = situation(domain(shop), "RU", "X" to "c")
        val nodeResult = matching.proceedWithAnswer(situation, listOf(0, 1)).nextState!!

        // Act.
        val question = question(nodeResult.getQuestion(situation))
        val wrong = nodeResult.proceedWithAnswer(situation, listOf(question.indexOf("Верно")))
        val right = nodeResult.proceedWithAnswer(situation, listOf(question.indexOf("Неверно")))

        // Assert.
        assertEquals("Верно ли, что чашку можно отправить без упаковки?", question.text)
        assertEquals(listOf("Верно", "Неверно"), question.optionTexts())
        assertTrue(
            wrong.explanation!!.text.startsWith(
                "Это неверно. Чтобы понять, что чашку можно отправить без упаковки, все из описанных выше факторов (чашка легкая, чашка прочная) должны выполняться."
            ),
            wrong.explanation.text,
        )
        assertEquals(ExplanationType.Error, wrong.explanation.type)
        assertEquals(false, wrong.explanation.shouldPause)
        assertEquals(Explanation("Верно.", ExplanationType.Success, shouldPause = false), right.explanation)
        assertEquals("Какой следующий шаг необходим для решения задачи?", question(right.nextState!!.getQuestion(situation)).text)
    }

    /** Объяснение неверного итога говорит, выполняются ли факторы на самом деле, а не в выбранном студентом варианте. */
    @Test
    fun nodeResultMistakeDescribesActualFactors() {
        // Arrange.
        val tree = tree($$"""
            tpg Parcel(X: item) {
                agg and {
                    _ -[light]-> { ask (X.weight > 3) { true -> { conclude: error }; false -> { conclude: correct }; } as heavy; };
                    _ -[sturdy]-> { ask (X.fragile) { true -> { conclude: error }; false -> { conclude: correct }; } as fragile; };
                    correct -> { ask (X.price > 100) { true -> { conclude: error }; false -> { conclude: correct }; } as expensive; };
                    error -> { conclude: error };
                } as sending;
            }
            [ alias = "parcel"; RU.description = "посылку можно отправить"; ]
            meta for sending [ alias = "sending"; RU.description = "${X}[case='в'] ${$branchResult == BranchResult:CORRECT ? 'можно' : 'нельзя'} отправить без упаковки"; ]
            meta for light [ alias = "light"; RU.description = "${X}[case='и'] ${$branchResult == BranchResult:CORRECT ? 'легкая' : 'тяжелая'}"; ]
            meta for sturdy [ alias = "sturdy"; RU.description = "${X}[case='и'] ${$branchResult == BranchResult:CORRECT ? 'прочная' : 'хрупкая'}"; ]
        """)
        val matching = SequentialStrategy.buildWithInfo(tree.mainBranch).info.nodeStates[tree.element<BranchAggregationNode>("sending")]!!
        val situation = situation(domain(shop), "RU", "X" to "c")
        val nodeResult = matching.proceedWithAnswer(situation, listOf(0, 1)).nextState!!
        val question = question(nodeResult.getQuestion(situation))

        // Act.
        val wrong = nodeResult.proceedWithAnswer(situation, listOf(question.indexOf("Верно")))

        // Assert.
        assertTrue(wrong.explanation!!.text.endsWith("Однако в данном случае это не так."), wrong.explanation.text)
    }

    /** Если узел может дать NULL, в вопросе об итоге есть «Не влияет» с отдельным объяснением. */
    @Test
    fun nodeResultQuestionOffersNoEffectWhenNodeCanBeNull() {
        // Arrange.
        val tree = tree($$"""
            tpg Parcel(X: item) {
                agg and {
                    _ -[light]-> { ask (X.weight > 3) { true -> { conclude: error }; false -> { conclude: null }; } as heavy; };
                    _ -[sturdy]-> { ask (X.fragile) { true -> { conclude: error }; false -> { conclude: null }; } as fragile; };
                    correct -> { ask (X.price > 100) { true -> { conclude: error }; false -> { conclude: correct }; } as expensive; };
                    error -> { conclude: error };
                    null -> { conclude: correct };
                } as sending;
            }
            [ alias = "parcel"; RU.description = "посылку можно отправить"; ]
            meta for sending [ alias = "sending"; RU.description = "${X}[case='в'] можно отправить без упаковки"; ]
            meta for light [ alias = "light"; RU.description = "${X}[case='и'] ${$branchResult == BranchResult:ERROR ? 'тяжелая' : 'легкая'}"; ]
            meta for sturdy [ alias = "sturdy"; RU.description = "${X}[case='и'] ${$branchResult == BranchResult:ERROR ? 'хрупкая' : 'прочная'}"; ]
        """)
        val matching = SequentialStrategy.buildWithInfo(tree.mainBranch).info.nodeStates[tree.element<BranchAggregationNode>("sending")]!!
        val situation = situation(domain(shop), "RU", "X" to "a")
        val nodeResult = matching.proceedWithAnswer(situation, listOf(1, 1)).nextState!!

        // Act.
        val question = question(nodeResult.getQuestion(situation))
        val noEffect = nodeResult.proceedWithAnswer(situation, listOf(question.indexOf("Не влияет")))

        // Assert.
        assertEquals(listOf("Верно", "Неверно", "Не влияет"), question.optionTexts())
        assertEquals(
            Explanation(
                "Это неверно, поскольку в данной ситуации все из описанных выше факторов (ваза легкая, ваза прочная) не влияют на решение, а значит, об общем результате в данном случае говорить не приходится.",
                ExplanationType.Error,
                shouldPause = false,
            ),
            noEffect.explanation,
        )
    }

    /** В тривиальной ветви (исходы агрегации сразу дают заключения) итог узла не спрашивается - после сопоставления ветвь завершена. */
    @Test
    fun trivialBranchSkipsNodeResultQuestion() {
        // Arrange.
        val tree = tree($$"""
            tpg Parcel(X: item) {
                agg and {
                    _ -[light]-> { ask (X.weight > 3) { true -> { conclude: error }; false -> { conclude: correct }; } as heavy; };
                    _ -[sturdy]-> { ask (X.fragile) { true -> { conclude: error }; false -> { conclude: correct }; } as fragile; };
                    correct -> { conclude: correct };
                    error -> { conclude: error };
                } as sending;
            }
            [ alias = "parcel"; ]
            meta for sending [ alias = "sending"; RU.description = "${X}[case='в'] ${$branchResult == BranchResult:CORRECT ? 'можно' : 'нельзя'} отправить"; ]
            meta for light [ alias = "light"; RU.description = "${X}[case='и'] ${$branchResult == BranchResult:CORRECT ? 'легкая' : 'тяжелая'}"; ]
            meta for sturdy [ alias = "sturdy"; RU.description = "${X}[case='и'] ${$branchResult == BranchResult:CORRECT ? 'прочная' : 'хрупкая'}"; ]
        """)
        val end = EndQuestionState()
        val automata = SequentialStrategy.buildAndFinalize(tree.mainBranch, end)
        val situation = situation(domain(shop), "RU", "X" to "c")

        // Act.
        val change = automata.initState.proceedWithAnswer(situation, listOf(0, 1))

        // Assert.
        assertSame(end, assertIs<RedirectQuestionState>(change.nextState).redirectsTo())
    }

    /** MUTEX: студент выбирает, какой из взаимоисключающих случаев имеет место (или никакой). */
    @Test
    fun mutexAsksWhichCaseApplies() {
        // Arrange.
        val tree = tree($$"""
            tpg Parcel(X: item) {
                agg mutex {
                    _ -[heavyCase]-> { ask (X.weight > 3) { true -> { conclude: error }; false -> { conclude: null }; } as heavy; };
                    _ -[fragileCase]-> { ask (X.weight <= 3 and X.fragile) { true -> { conclude: error }; false -> { conclude: null }; } as fragile; };
                    error -> { ask (X.price > 100) { true -> { conclude: error }; false -> { conclude: correct }; } as expensive; };
                    null -> { conclude: correct };
                } as problem;
            }
            [ alias = "parcel"; RU.description = "посылку можно отправить"; ]
            meta for problem [ alias = "problem"; ]
            meta for heavyCase [ alias = "heavyCase"; RU.description = "${X}[case='и'] слишком тяжелая"; ]
            meta for fragileCase [ alias = "fragileCase"; RU.description = "${X}[case='и'] слишком хрупкая"; ]
            meta for expensive [ alias = "expensive"; RU.asNextStep = "Проверить цену."; ]
        """)
        val state = SequentialStrategy.buildWithInfo(tree.mainBranch).info.nodeStates[tree.element<BranchAggregationNode>("problem")]!!
        val situation = situation(domain(shop), "RU", "X" to "c")

        // Act.
        val question = question(state.getQuestion(situation))
        val wrong = state.proceedWithAnswer(situation, listOf(question.indexOf("чашка слишком тяжелая")))
        val right = state.proceedWithAnswer(situation, listOf(question.indexOf("чашка слишком хрупкая")))

        // Assert.
        assertEquals("Что из перечисленного применимо в данной ситуации?", question.text)
        assertEquals(listOf("чашка слишком тяжелая", "чашка слишком хрупкая", "Ничто из вышеперечисленного не применимо"), question.optionTexts())
        assertEquals("Это неверно. В данной ситуации чашка слишком хрупкая.", wrong.explanation!!.text)
        assertEquals(true, wrong.explanation.shouldPause)
        assertEquals(Explanation("Верно.", ExplanationType.Success, shouldPause = false), right.explanation)
        assertEquals("Какой следующий шаг необходим для решения задачи?", question(change(right.nextState!!.getQuestion(situation)).nextState!!.getQuestion(situation)).text)
    }

    /** MUTEX: когда ни один случай не имеет места, верен вариант «Ничто из вышеперечисленного не применимо». */
    @Test
    fun mutexNoneApplies() {
        // Arrange.
        val tree = tree($$"""
            tpg Parcel(X: item) {
                agg mutex {
                    _ -[heavyCase]-> { ask (X.weight > 3) { true -> { conclude: error }; false -> { conclude: null }; } as heavy; };
                    _ -[fragileCase]-> { ask (X.weight <= 3 and X.fragile) { true -> { conclude: error }; false -> { conclude: null }; } as fragile; };
                    error -> { ask (X.price > 100) { true -> { conclude: error }; false -> { conclude: correct }; } as expensive; };
                    null -> { conclude: correct };
                } as problem;
            }
            [ alias = "parcel"; RU.description = "посылку можно отправить"; ]
            meta for problem [ alias = "problem"; ]
            meta for heavyCase [ alias = "heavyCase"; RU.description = "${X}[case='и'] слишком тяжелая"; ]
            meta for fragileCase [ alias = "fragileCase"; RU.description = "${X}[case='и'] слишком хрупкая"; ]
        """)
        val state = SequentialStrategy.buildWithInfo(tree.mainBranch).info.nodeStates[tree.element<BranchAggregationNode>("problem")]!!
        val situation = situation(domain(shop), "RU", "X" to "b")
        val question = question(state.getQuestion(situation))

        // Act.
        val wrong = state.proceedWithAnswer(situation, listOf(question.indexOf("коробка слишком хрупкая")))
        val right = state.proceedWithAnswer(situation, listOf(question.indexOf("Ничто из вышеперечисленного не применимо")))

        // Assert.
        assertEquals("Это неверно. Ничто из вышеперечисленного не применимо", wrong.explanation!!.text)
        assertEquals(ExplanationType.Success, right.explanation!!.type)
    }

    /** MUTEX: выбор случая запоминается как предполагаемый результат его ветви; ошибочный случай с вопросами разбирается подробно. */
    @Test
    fun mutexWrongCaseIsDiscussed() {
        // Arrange.
        val tree = tree($$"""
            tpg Parcel(X: item) {
                agg mutex {
                    _ -[heavyCase]-> { ask (X.weight > 3) { true -> { conclude: error }; false -> { conclude: null }; } as heavy; };
                    _ -[fragileCase]-> {
                        ask (X.weight > 3) {
                            true -> { conclude: null };
                            false -> { ask (X.fragile) { true -> { conclude: error }; false -> { conclude: null }; } as fragile; };
                        } as fragileHeavy;
                    };
                    error -> { ask (X.price > 100) { true -> { conclude: error }; false -> { conclude: correct }; } as expensive; };
                    null -> { conclude: correct };
                } as problem;
            }
            [ alias = "parcel"; RU.description = "посылку можно отправить"; ]
            meta for problem [ alias = "problem"; ]
            meta for heavyCase [ alias = "heavyCase"; RU.description = "${X}[case='и'] слишком тяжелая"; ]
            meta for fragileCase [ alias = "fragileCase"; RU.description = "${X}[case='и'] слишком хрупкая"; ]
            meta for fragileHeavy [ alias = "fragileHeavy"; RU.endingCause = "Из-за веса"; ]
            meta for fragile [ alias = "fragile"; RU.endingCause = "Из-за хрупкости"; ]
        """)
        val state = SequentialStrategy.buildWithInfo(tree.mainBranch).info.nodeStates[tree.element<BranchAggregationNode>("problem")]!!
        val situation = situation(domain(shop), "RU", "X" to "a")
        val question = question(state.getQuestion(situation))

        // Act.
        val change = state.proceedWithAnswer(situation, listOf(question.indexOf("ваза слишком хрупкая")))

        // Assert.
        assertEquals(mapOf("fragileCase" to BranchResult.ERROR), situation.assumedResults)
        val branchQuestion = question(change.nextState!!.getQuestion(situation))
        assertEquals("Почему вы считаете, что ваза слишком хрупкая?", branchQuestion.text)
        assertEquals(listOf("Из-за хрупкости", "Из-за веса"), branchQuestion.optionTexts())
    }

    /** Объяснение неверного выбора случая в MUTEX - ошибка студента. */
    @Test
    fun mutexMistakeIsError() {
        // Arrange.
        val tree = tree($$"""
            tpg Parcel(X: item) {
                agg mutex {
                    _ -[heavyCase]-> { ask (X.weight > 3) { true -> { conclude: error }; false -> { conclude: null }; } as heavy; };
                    _ -[fragileCase]-> { ask (X.weight <= 3 and X.fragile) { true -> { conclude: error }; false -> { conclude: null }; } as fragile; };
                    error -> { ask (X.price > 100) { true -> { conclude: error }; false -> { conclude: correct }; } as expensive; };
                    null -> { conclude: correct };
                } as problem;
            }
            [ alias = "parcel"; RU.description = "посылку можно отправить"; ]
            meta for problem [ alias = "problem"; ]
            meta for heavyCase [ alias = "heavyCase"; RU.description = "${X}[case='и'] слишком тяжелая"; ]
            meta for fragileCase [ alias = "fragileCase"; RU.description = "${X}[case='и'] слишком хрупкая"; ]
        """)
        val state = SequentialStrategy.buildWithInfo(tree.mainBranch).info.nodeStates[tree.element<BranchAggregationNode>("problem")]!!
        val situation = situation(domain(shop), "RU", "X" to "c")
        val question = question(state.getQuestion(situation))

        // Act.
        val wrong = state.proceedWithAnswer(situation, listOf(question.indexOf("чашка слишком тяжелая")))

        // Assert.
        assertEquals(ExplanationType.Error, wrong.explanation!!.type)
    }

    /** Агрегация HYP не поддерживается: автомат для неё не строится. */
    @Test
    fun hypAggregationIsNotSupported() {
        // Arrange.
        val tree = tree("""
            tpg Parcel(X: item) {
                agg hyp {
                    _ -> { conclude: correct };
                    _ -> { conclude: error };
                    correct -> { conclude: correct };
                    error -> { conclude: error };
                };
            }
        """)

        // Act.
        val error = assertFailsWith<NotImplementedError> { SequentialStrategy.buildWithInfo(tree.mainBranch) }

        // Assert.
        assertEquals("An operation is not implemented: HYP aggregation not supported yet", error.message)
    }

    /** Разбор AND от сопоставления до заключения при верных ответах. */
    @Test
    fun andDialogTranscript() {
        // Arrange.
        val tree = tree($$"""
            tpg Parcel(X: item) {
                agg and {
                    _ -[light]-> { ask (X.weight > 3) { true -> { conclude: error }; false -> { conclude: correct }; } as heavy; };
                    _ -[sturdy]-> { ask (X.fragile) { true -> { conclude: error }; false -> { conclude: correct }; } as fragile; };
                    correct -> { ask (X.price > 100) { true -> { conclude: error }; false -> { conclude: correct }; } as expensive; };
                    error -> { conclude: error };
                } as sending;
            }
            [ alias = "parcel"; RU.description = "${X}[case='в'] ${$branchResult == BranchResult:CORRECT ? 'можно' : 'нельзя'} отправить"; ]
            meta for sending [ alias = "sending"; RU.description = "${X}[case='в'] ${$branchResult == BranchResult:CORRECT ? 'можно' : 'нельзя'} отправить без упаковки"; ]
            meta for light [ alias = "light"; RU.description = "${X}[case='и'] ${$branchResult == BranchResult:CORRECT ? 'легкая' : 'тяжелая'}"; ]
            meta for sturdy [ alias = "sturdy"; RU.description = "${X}[case='и'] ${$branchResult == BranchResult:CORRECT ? 'прочная' : 'хрупкая'}"; ]
            meta for expensive [ alias = "expensive"; RU.asNextStep = "Проверить цену."; ]
        """)
        val output = SequentialStrategy.buildWithInfo(tree.mainBranch)
        output.automata.finalize(EndQuestionState())
        val sending = output.info.nodeStates[tree.element<BranchAggregationNode>("sending")]!!
        val situation = situation(domain(shop), "RU", "X" to "b")

        // Act.
        val transcript = Dialogs.walk(
            sending, situation,
            match("Неверно", "Верно"),
            pick("Верно"),
            pick("Проверить цену."),
            pick("Нет"),
            pick("Можно заключить, что коробку можно отправить."),
        )

        // Assert.
        assertEquals(
            """
            ? Пожалуйста, сопоставьте ответы
              - коробка легкая
              - коробка прочная
              = Верно
              = Неверно
            > коробка легкая = Неверно; коробка прочная = Верно
            ! Error: Это неверно, поскольку коробка легкая.
            ? Верно ли, что коробку можно отправить без упаковки?
              - Верно
              - Неверно
            > Верно
            ! Success (auto): Верно.
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
            """.trimIndent(),
            transcript.text,
        )
    }
}
