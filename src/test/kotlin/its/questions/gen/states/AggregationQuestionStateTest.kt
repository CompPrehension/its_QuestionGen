package its.questions.gen.states

import its.model.nodes.BranchAggregationNode
import its.model.nodes.BranchResult
import its.questions.gen.QuestionGenFixtures.domain
import its.questions.gen.QuestionGenFixtures.element
import its.questions.gen.QuestionGenFixtures.matchingTexts
import its.questions.gen.QuestionGenFixtures.optionTexts
import its.questions.gen.QuestionGenFixtures.question
import its.questions.gen.QuestionGenFixtures.situation
import its.questions.gen.QuestionGenFixtures.tree
import its.questions.gen.formulations.LocalizationRU
import its.questions.gen.strategies.SequentialStrategy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals

/**
 * Вопрос-сопоставление [AggregationQuestionState] к узлу агрегации AND/OR: столбцы, оценка ответа,
 * объяснения и выбор ветви для подробного разбора. Ответ на сопоставление - номер столбца для каждой строки:
 * 0 - «Верно», 1 - «Неверно», 2 - «Не влияет».
 */
class AggregationQuestionStateTest {

    private val shop = """
        class item {
            obj prop weight: int ;
            obj prop price: int ;
            obj prop fragile: bool ;
        } [ RU.localizedName = "предмет" ; ]
        obj a : item { weight = 5 ; price = 10 ; fragile = true ; } [ RU.localizedName = "ваза" ; ]
        obj b : item { weight = 1 ; price = 10 ; fragile = false ; } [ RU.localizedName = "коробка" ; ]
        obj c : item { weight = 1 ; price = 10 ; fragile = true ; } [ RU.localizedName = "чашка" ; ]
    """

    /** Сопоставление: строки - описания ветвей в верном виде, столбцы - «Верно» и «Неверно», если ни одна ветвь не даёт NULL. */
    @Test
    fun matchingQuestionWithoutNullColumn() {
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
            [ alias = "parcel"; ]
            meta for sending [ alias = "sending"; ]
            meta for light [ alias = "light"; RU.description = "${X}[case='и'] ${$branchResult == BranchResult:CORRECT ? 'легкая' : 'тяжелая'}"; ]
            meta for sturdy [ alias = "sturdy"; RU.description = "${X}[case='и'] ${$branchResult == BranchResult:CORRECT ? 'прочная' : 'хрупкая'}"; ]
        """)
        val state = SequentialStrategy.buildWithInfo(tree.mainBranch).info.nodeStates[tree.element<BranchAggregationNode>("sending")]!!

        // Act.
        val question = question(state.getQuestion(situation(domain(shop), "RU", "X" to "a")))

        // Assert.
        assertIs<AggregationQuestionState<*, *>>(state)
        assertEquals("Пожалуйста, сопоставьте ответы", question.text)
        assertEquals(QuestionType.matching, question.type)
        assertEquals(listOf("ваза легкая", "ваза прочная"), question.optionTexts())
        assertEquals(listOf("Верно", "Неверно"), question.matchingTexts())
    }

    /** Регрессия: если хоть одна ветвь может дать NULL, добавляется столбец «Не влияет» (или nullFormulation узла). */
    @Test
    fun matchingQuestionHasNullColumnWhenBranchCanBeNull() {
        // Arrange.
        val tree = tree($$"""
            tpg Parcel(X: item) {
                agg and {
                    _ -[light]-> { ask (X.weight > 3) { true -> { conclude: error }; false -> { conclude: correct }; } as heavy; };
                    _ -[sturdy]-> { ask (X.fragile) { true -> { conclude: error }; false -> { conclude: null }; } as fragile; };
                    correct -> {
                        agg or {
                            _ -[light2]-> { ask (X.weight > 3) { true -> { conclude: error }; false -> { conclude: correct }; } as heavy2; };
                            _ -[sturdy2]-> { ask (X.fragile) { true -> { conclude: error }; false -> { conclude: null }; } as fragile2; };
                            correct -> { conclude: correct };
                            error -> { conclude: error };
                        } as sendingCustomNull;
                    };
                    error -> { conclude: error };
                } as sending;
            }
            [ alias = "parcel"; ]
            meta for sending [ alias = "sending"; ]
            meta for sendingCustomNull [ alias = "sendingCustomNull"; RU.nullFormulation = "Не важно для отправки"; ]
            meta for light [ alias = "light"; RU.description = "${X}[case='и'] легкая"; ]
            meta for sturdy [ alias = "sturdy"; RU.description = "${X}[case='и'] прочная"; ]
            meta for light2 [ alias = "light2"; RU.description = "${X}[case='и'] легкая"; ]
            meta for sturdy2 [ alias = "sturdy2"; RU.description = "${X}[case='и'] прочная"; ]
        """)
        val info = SequentialStrategy.buildWithInfo(tree.mainBranch).info
        val situation = situation(domain(shop), "RU", "X" to "a")

        // Act.
        val defaultNull = question(info.nodeStates[tree.element<BranchAggregationNode>("sending")]!!.getQuestion(situation))
        val customNull = question(info.nodeStates[tree.element<BranchAggregationNode>("sendingCustomNull")]!!.getQuestion(situation))

        // Assert.
        assertEquals(listOf("Верно", "Неверно", "Не влияет"), defaultNull.matchingTexts())
        assertEquals(listOf("Верно", "Неверно", "Не важно для отправки"), customNull.matchingTexts())
    }

    /** Верное сопоставление объясняет, что из него следует для узла, и запоминает ответы как предполагаемые результаты ветвей. */
    @Test
    fun correctMatchingExplainsNodeResult() {
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
            [ alias = "parcel"; ]
            meta for sending [ alias = "sending"; RU.description = "${X}[case='в'] ${$branchResult == BranchResult:CORRECT ? 'можно' : 'нельзя'} отправить без упаковки"; ]
            meta for light [ alias = "light"; RU.description = "${X}[case='и'] ${$branchResult == BranchResult:CORRECT ? 'легкая' : 'тяжелая'}"; ]
            meta for sturdy [ alias = "sturdy"; RU.description = "${X}[case='и'] ${$branchResult == BranchResult:CORRECT ? 'прочная' : 'хрупкая'}"; ]
        """)
        val state = SequentialStrategy.buildWithInfo(tree.mainBranch).info.nodeStates[tree.element<BranchAggregationNode>("sending")]!!
        val situation = situation(domain(shop), "RU", "X" to "c")

        // Act.
        val change = state.proceedWithAnswer(situation, listOf(0, 1))

        // Assert.
        assertEquals(LocalizationRU.AGGREGATION_CORRECT_EXPL("чашку нельзя отправить без упаковки", "чашка хрупкая"), change.explanation!!.text)
        assertEquals(true, change.explanation.shouldPause)
        assertEquals(mapOf("light" to BranchResult.CORRECT, "sturdy" to BranchResult.ERROR), situation.assumedResults)
        assertEquals("Верно ли, что чашку можно отправить без упаковки?", question(change.nextState!!.getQuestion(situation)).text)
    }

    /** Верное сопоставление - не ошибка студента. */
    @Test
    fun correctMatchingIsNotError() {
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
            [ alias = "parcel"; ]
            meta for sending [ alias = "sending"; RU.description = "${X}[case='в'] ${$branchResult == BranchResult:CORRECT ? 'можно' : 'нельзя'} отправить без упаковки"; ]
            meta for light [ alias = "light"; RU.description = "${X}[case='и'] ${$branchResult == BranchResult:CORRECT ? 'легкая' : 'тяжелая'}"; ]
            meta for sturdy [ alias = "sturdy"; RU.description = "${X}[case='и'] ${$branchResult == BranchResult:CORRECT ? 'прочная' : 'хрупкая'}"; ]
        """)
        val state = SequentialStrategy.buildWithInfo(tree.mainBranch).info.nodeStates[tree.element<BranchAggregationNode>("sending")]!!

        // Act.
        val change = state.proceedWithAnswer(situation(domain(shop), "RU", "X" to "c"), listOf(0, 1))

        // Assert.
        assertNotEquals(ExplanationType.Error, change.explanation!!.type)
    }

    /** Неверно оценённые ветви перечисляются в фактическом виде; далее - выбор ветви для разбора. */
    @Test
    fun incorrectBranchesAreExplained() {
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
            [ alias = "parcel"; ]
            meta for sending [ alias = "sending"; ]
            meta for light [ alias = "light"; RU.description = "${X}[case='и'] ${$branchResult == BranchResult:CORRECT ? 'легкая' : 'тяжелая'}"; ]
            meta for sturdy [ alias = "sturdy"; RU.description = "${X}[case='и'] ${$branchResult == BranchResult:CORRECT ? 'прочная' : 'хрупкая'}"; ]
        """)
        val state = SequentialStrategy.buildWithInfo(tree.mainBranch).info.nodeStates[tree.element<BranchAggregationNode>("sending")]!!
        val situation = situation(domain(shop), "RU", "X" to "a")

        // Act.
        val change = state.proceedWithAnswer(situation, listOf(0, 0))

        // Assert.
        assertEquals(Explanation("Это неверно, поскольку ваза тяжелая, ваза хрупкая.", ExplanationType.Error), change.explanation)
        val select = question(change.nextState!!.getQuestion(situation))
        assertEquals("В чем бы вы хотели разобраться подробнее?", select.text)
        assertEquals(listOf("Почему ваза тяжелая?", "Почему ваза хрупкая?"), select.optionTexts())
    }

    /** Регрессия: только пропущенные (отмеченные как не влияющие) ветви перечисляются в объяснении о пропуске. */
    @Test
    fun missedBranchesAreExplained() {
        // Arrange.
        val tree = tree($$"""
            tpg Parcel(X: item) {
                agg and {
                    _ -[light]-> { ask (X.weight > 3) { true -> { conclude: error }; false -> { conclude: correct }; } as heavy; };
                    _ -[sturdy]-> { ask (X.fragile) { true -> { conclude: error }; false -> { conclude: null }; } as fragile; };
                    correct -> { ask (X.price > 100) { true -> { conclude: error }; false -> { conclude: correct }; } as expensive; };
                    error -> { conclude: error };
                } as sending;
            }
            [ alias = "parcel"; ]
            meta for sending [ alias = "sending"; ]
            meta for light [ alias = "light"; RU.description = "${X}[case='и'] ${$branchResult == BranchResult:CORRECT ? 'легкая' : 'тяжелая'}"; ]
            meta for sturdy [ alias = "sturdy"; RU.description = "${X}[case='и'] ${$branchResult == BranchResult:ERROR ? 'хрупкая' : 'прочная'}"; ]
        """)
        val state = SequentialStrategy.buildWithInfo(tree.mainBranch).info.nodeStates[tree.element<BranchAggregationNode>("sending")]!!

        // Act.
        val change = state.proceedWithAnswer(situation(domain(shop), "RU", "X" to "a"), listOf(2, 1))

        // Assert.
        assertEquals(
            Explanation("Это неверно, поскольку вы не упомянули, что ваза тяжелая - это влияет на ситуацию в данном случае.", ExplanationType.Error),
            change.explanation,
        )
    }

    /** Регрессия: неверно оценённые и пропущенные ветви объясняются двумя предложениями через пробел. */
    @Test
    fun incorrectAndMissedBranchesAreSeparateSentences() {
        // Arrange.
        val tree = tree($$"""
            tpg Parcel(X: item) {
                agg and {
                    _ -[light]-> { ask (X.weight > 3) { true -> { conclude: error }; false -> { conclude: correct }; } as heavy; };
                    _ -[sturdy]-> { ask (X.fragile) { true -> { conclude: error }; false -> { conclude: null }; } as fragile; };
                    correct -> { ask (X.price > 100) { true -> { conclude: error }; false -> { conclude: correct }; } as expensive; };
                    error -> { conclude: error };
                } as sending;
            }
            [ alias = "parcel"; ]
            meta for sending [ alias = "sending"; ]
            meta for light [ alias = "light"; RU.description = "${X}[case='и'] ${$branchResult == BranchResult:CORRECT ? 'легкая' : 'тяжелая'}"; ]
            meta for sturdy [ alias = "sturdy"; RU.description = "${X}[case='и'] ${$branchResult == BranchResult:ERROR ? 'хрупкая' : 'прочная'}"; ]
        """)
        val state = SequentialStrategy.buildWithInfo(tree.mainBranch).info.nodeStates[tree.element<BranchAggregationNode>("sending")]!!

        // Act.
        val change = state.proceedWithAnswer(situation(domain(shop), "RU", "X" to "a"), listOf(2, 0))

        // Assert.
        assertEquals(
            "Это неверно, поскольку ваза хрупкая. Вы также не упомянули, что ваза тяжелая - это влияет на ситуацию в данном случае.",
            change.explanation!!.text,
        )
    }

    /** Для AND с итогом «неверно» верная ветвь, отмеченная как не влияющая, не считается пропущенной (и для OR с итогом «верно» - неверная). */
    @Test
    fun branchesIrrelevantToResultMayBeMarkedAsNoEffect() {
        // Arrange.
        val tree = tree($$"""
            tpg Parcel(X: item) {
                agg and {
                    _ -[light]-> { ask (X.weight > 3) { true -> { conclude: error }; false -> { conclude: correct }; } as heavy; };
                    _ -[sturdy]-> { ask (X.fragile) { true -> { conclude: error }; false -> { conclude: null }; } as fragile; };
                    correct -> {
                        agg or {
                            _ -[light2]-> { ask (X.weight > 3) { true -> { conclude: error }; false -> { conclude: correct }; } as heavy2; };
                            _ -[sturdy2]-> { ask (X.fragile) { true -> { conclude: error }; false -> { conclude: null }; } as fragile2; };
                            correct -> { conclude: correct };
                            error -> { conclude: error };
                        } as sendingOr;
                    };
                    error -> { conclude: error };
                } as sendingAnd;
            }
            [ alias = "parcel"; ]
            meta for sendingAnd [ alias = "sendingAnd"; RU.description = "${X}[case='в'] ${$branchResult == BranchResult:CORRECT ? 'можно' : 'нельзя'} отправить"; ]
            meta for sendingOr [ alias = "sendingOr"; RU.description = "${X}[case='в'] ${$branchResult == BranchResult:CORRECT ? 'можно' : 'нельзя'} отправить"; ]
            meta for light [ alias = "light"; RU.description = "${X}[case='и'] ${$branchResult == BranchResult:CORRECT ? 'легкая' : 'тяжелая'}"; ]
            meta for sturdy [ alias = "sturdy"; RU.description = "${X}[case='и'] ${$branchResult == BranchResult:ERROR ? 'хрупкая' : 'прочная'}"; ]
            meta for light2 [ alias = "light2"; RU.description = "${X}[case='и'] ${$branchResult == BranchResult:CORRECT ? 'легкая' : 'тяжелая'}"; ]
            meta for sturdy2 [ alias = "sturdy2"; RU.description = "${X}[case='и'] ${$branchResult == BranchResult:ERROR ? 'хрупкая' : 'прочная'}"; ]
        """)
        val info = SequentialStrategy.buildWithInfo(tree.mainBranch).info

        // Act.
        val and = info.nodeStates[tree.element<BranchAggregationNode>("sendingAnd")]!!.proceedWithAnswer(situation(domain(shop), "RU", "X" to "c"), listOf(2, 1))
        val or = info.nodeStates[tree.element<BranchAggregationNode>("sendingOr")]!!.proceedWithAnswer(situation(domain(shop), "RU", "X" to "c"), listOf(0, 2))

        // Assert.
        assertEquals(LocalizationRU.AGGREGATION_CORRECT_EXPL("чашку нельзя отправить", "чашка хрупкая"), and.explanation!!.text)
        assertEquals(LocalizationRU.AGGREGATION_CORRECT_EXPL("чашку можно отправить", "чашка легкая"), or.explanation!!.text)
    }

    /** Ветвь, действительно не влияющая на ситуацию и отмеченная как «Не влияет», оценена верно. */
    @Test
    fun branchWithNullResultMarkedAsNoEffectIsCorrect() {
        // Arrange.
        val tree = tree($$"""
            tpg Parcel(X: item) {
                agg and {
                    _ -[light]-> { ask (X.weight > 3) { true -> { conclude: error }; false -> { conclude: correct }; } as heavy; };
                    _ -[sturdy]-> { ask (X.fragile) { true -> { conclude: error }; false -> { conclude: null }; } as fragile; };
                    correct -> { ask (X.price > 100) { true -> { conclude: error }; false -> { conclude: correct }; } as expensive; };
                    error -> { conclude: error };
                } as sending;
            }
            [ alias = "parcel"; ]
            meta for sending [ alias = "sending"; RU.description = "${X}[case='в'] ${$branchResult == BranchResult:CORRECT ? 'можно' : 'нельзя'} отправить"; ]
            meta for light [ alias = "light"; RU.description = "${X}[case='и'] ${$branchResult == BranchResult:CORRECT ? 'легкая' : 'тяжелая'}"; ]
            meta for sturdy [ alias = "sturdy"; RU.description = "${X}[case='и'] ${$branchResult == BranchResult:ERROR ? 'хрупкая' : 'прочная'}"; ]
        """)
        val state = SequentialStrategy.buildWithInfo(tree.mainBranch).info.nodeStates[tree.element<BranchAggregationNode>("sending")]!!
        val situation = situation(domain(shop), "RU", "X" to "b")

        // Act.
        val change = state.proceedWithAnswer(situation, listOf(0, 2))

        // Assert.
        assertEquals(LocalizationRU.AGGREGATION_CORRECT_EXPL("коробку можно отправить", "коробка легкая"), change.explanation!!.text)
        assertEquals("Верно ли, что коробку можно отправить?", question(change.nextState!!.getQuestion(situation)).text)
    }

    /** Выбор ветви для разбора ведёт в её полный разбор, который начинается с вопроса, почему студент так считал. */
    @Test
    fun chosenBranchIsDiscussedInFull() {
        // Arrange.
        val tree = tree($$"""
            tpg Parcel(X: item) {
                agg and {
                    _ -[light]-> {
                        ask (X.weight > 10) {
                            true -> { conclude: error };
                            false -> { ask (X.weight > 3) { true -> { conclude: error }; false -> { conclude: correct }; } as heavy; };
                        } as veryHeavy;
                    };
                    _ -[sturdy]-> { ask (X.fragile) { true -> { conclude: error }; false -> { conclude: correct }; } as fragile; };
                    correct -> { ask (X.price > 100) { true -> { conclude: error }; false -> { conclude: correct }; } as expensive; };
                    error -> { conclude: error };
                } as sending;
            }
            [ alias = "parcel"; ]
            meta for sending [ alias = "sending"; ]
            meta for light [ alias = "light"; RU.description = "${X}[case='и'] ${$branchResult == BranchResult:CORRECT ? 'легкая' : 'тяжелая'}"; ]
            meta for sturdy [ alias = "sturdy"; RU.description = "${X}[case='и'] ${$branchResult == BranchResult:CORRECT ? 'прочная' : 'хрупкая'}"; ]
            meta for veryHeavy [ alias = "veryHeavy"; RU.endingCause = "Из-за очень большого веса"; ]
            meta for heavy [ alias = "heavy"; RU.endingCause = "Из-за веса"; ]
        """)
        val state = SequentialStrategy.buildWithInfo(tree.mainBranch).info.nodeStates[tree.element<BranchAggregationNode>("sending")]!!
        val situation = situation(domain(shop), "RU", "X" to "a")
        val select = state.proceedWithAnswer(situation, listOf(0, 1)).nextState!!

        // Act.
        val change = select.getQuestion(situation)

        // Assert.
        val skipped = its.questions.gen.QuestionGenFixtures.change(change)
        assertEquals(Explanation("Давайте разберемся.", shouldPause = false), skipped.explanation)
        val branchQuestion = question(skipped.nextState!!.getQuestion(situation))
        assertEquals("Почему вы считаете, что ваза легкая?", branchQuestion.text)
        assertEquals(listOf("Из-за веса", "Из-за очень большого веса"), branchQuestion.optionTexts())
    }

    /** Если разбирать выбранную ветвь не о чем, «Давайте разберемся.» не обещается. */
    @Test
    fun branchWithoutQuestionsIsNotPromisedDiscussion() {
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
            [ alias = "parcel"; ]
            meta for sending [ alias = "sending"; ]
            meta for light [ alias = "light"; RU.description = "${X}[case='и'] ${$branchResult == BranchResult:CORRECT ? 'легкая' : 'тяжелая'}"; ]
            meta for sturdy [ alias = "sturdy"; RU.description = "${X}[case='и'] ${$branchResult == BranchResult:CORRECT ? 'прочная' : 'хрупкая'}"; ]
        """)
        val state = SequentialStrategy.buildWithInfo(tree.mainBranch).info.nodeStates[tree.element<BranchAggregationNode>("sending")]!!
        val situation = situation(domain(shop), "RU", "X" to "a")
        val select = state.proceedWithAnswer(situation, listOf(0, 0)).nextState!!

        // Act.
        val change = select.proceedWithAnswer(situation, listOf(0))

        // Assert.
        assertNotEquals("Давайте разберемся.", change.explanation?.text)
    }
}
